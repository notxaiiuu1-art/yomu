package app.orbis.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The whole bank lives on the device: every account, transfer, request, goal and budget is kept
 * in one JSON file. Transfers between accounts on this device move real (in-app) money; transfers
 * to unknown account IDs simply leave the bank.
 */
object Bank {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var file: File

    private val _state = MutableStateFlow(BankState())
    val state: StateFlow<BankState> = _state.asStateFlow()

    /** The account that is currently signed in, or null on the login screen. */
    private val _session = MutableStateFlow<String?>(null)
    val session: StateFlow<String?> = _session.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "bank.json")
        _state.value = runCatching { json.decodeFromString(BankState.serializer(), file.readText()) }.getOrElse { BankState() }
    }

    private fun save(s: BankState) {
        val text = json.encodeToString(BankState.serializer(), s)
        io.execute {
            val tmp = File(file.parentFile, "bank.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }

    @Synchronized
    private fun update(f: (BankState) -> BankState): BankState {
        val next = f(_state.value)
        _state.value = next
        save(next)
        return next
    }

    private fun uid() = UUID.randomUUID().toString()

    // ------------------------------------------------------------------ Accounts & session

    fun newAccountId(prefix: String = _state.value.settings.idPrefix): String {
        val letters = ('A'..'Z')
        val p = prefix.uppercase().filter { it.isLetterOrDigit() }.take(3).ifEmpty { "OR" }
        while (true) {
            val id = "$p-${(10..99).random()}${letters.random()}${letters.random()}${letters.random()}"
            if (_state.value.account(id) == null) return id
        }
    }

    fun createAccount(name: String, pin: String, startBalance: Long, colorIndex: Int): Account {
        val salt = Security.newSalt()
        val acc = Account(
            id = newAccountId(),
            name = name.trim(),
            pinSalt = salt,
            pinHash = Security.hash(pin, salt),
            balance = 0,
            colorIndex = colorIndex,
        )
        update { it.copy(accounts = it.accounts + acc) }
        if (startBalance > 0) topUp(acc.id, startBalance, "Startguthaben")
        return _state.value.account(acc.id)!!
    }

    fun login(id: String) {
        _session.value = id
        update { it.copy(settings = it.settings.copy(lastAccount = id)) }
        processRecurring()
    }

    fun logout() {
        _session.value = null
    }

    fun updateAccount(id: String, f: (Account) -> Account) = update { s ->
        s.copy(accounts = s.accounts.map { if (it.id == id) f(it) else it })
    }

    fun changePin(id: String, pin: String) = updateAccount(id) {
        val salt = Security.newSalt()
        it.copy(pinSalt = salt, pinHash = Security.hash(pin, salt))
    }

    fun deleteAccount(id: String) {
        update { s ->
            s.copy(
                accounts = s.accounts.filterNot { it.id == id },
                contacts = s.contacts.filterNot { it.owner == id },
                goals = s.goals.filterNot { it.owner == id },
                budgets = s.budgets.filterNot { it.owner == id },
                recurring = s.recurring.filterNot { it.owner == id },
                requests = s.requests.map {
                    if (it.status == RequestStatus.PENDING && (it.payer == id || it.requester == id)) {
                        it.copy(status = RequestStatus.CANCELLED)
                    } else it
                },
                settings = if (s.settings.lastAccount == id) s.settings.copy(lastAccount = null) else s.settings,
            )
        }
        if (_session.value == id) _session.value = null
    }

    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    /** Name for an account ID: a local account, a saved contact, or the ID itself. */
    fun nameFor(owner: String?, id: String): String {
        val s = _state.value
        return s.account(id)?.name
            ?: s.contacts.firstOrNull { it.owner == owner && it.accountId.equals(id, true) }?.name
            ?: id
    }

    // ------------------------------------------------------------------ Money movement

    fun spentToday(id: String): Long {
        val today = Dates.startOfDay(System.currentTimeMillis())
        return _state.value.txns
            .filter { it.from == id && it.time >= today && (it.kind == TxnKind.TRANSFER || it.kind == TxnKind.REQUEST || it.kind == TxnKind.RECURRING) }
            .sumOf { it.amount }
    }

    sealed interface Result {
        data class Ok(val txn: Txn) : Result
        data class Error(val message: String) : Result
    }

    @Synchronized
    fun transfer(
        fromId: String,
        toId: String,
        amount: Long,
        note: String,
        category: String,
        kind: TxnKind = TxnKind.TRANSFER,
        toNameOverride: String? = null,
    ): Result {
        val s = _state.value
        val from = s.account(fromId) ?: return Result.Error("Konto nicht gefunden.")
        val target = toId.trim().uppercase()
        if (amount <= 0) return Result.Error("Bitte einen Betrag eingeben.")
        if (target.isEmpty()) return Result.Error("Bitte einen Empfänger wählen.")
        if (target == from.id) return Result.Error("Du kannst nicht an dich selbst senden.")
        if (amount > from.balance) return Result.Error("Nicht genug Guthaben.")
        if (from.dailyLimit > 0 && spentToday(from.id) + amount > from.dailyLimit) {
            return Result.Error("Tageslimit von ${Money.format(from.dailyLimit, s.settings)} überschritten.")
        }
        val to = s.account(target)
        val txn = Txn(
            id = uid(),
            from = from.id,
            to = target,
            fromName = from.name,
            toName = toNameOverride ?: to?.name ?: nameFor(from.id, target),
            amount = amount,
            note = note.trim(),
            category = category,
            kind = kind,
        )
        update { st ->
            st.copy(
                accounts = st.accounts.map {
                    when (it.id) {
                        from.id -> it.copy(balance = it.balance - amount)
                        target -> it.copy(balance = it.balance + amount)
                        else -> it
                    }
                },
                txns = st.txns + txn,
            )
        }
        return Result.Ok(txn)
    }

    /** Puts new money into an account (deposit / top-up). */
    fun topUp(id: String, amount: Long, note: String): Txn? {
        if (amount <= 0) return null
        val acc = _state.value.account(id) ?: return null
        val txn = Txn(
            id = uid(), from = null, to = id, fromName = "Einzahlung", toName = acc.name,
            amount = amount, note = note.trim(), category = Category.SALARY.key, kind = TxnKind.TOPUP,
        )
        update { st ->
            st.copy(
                accounts = st.accounts.map { if (it.id == id) it.copy(balance = it.balance + amount) else it },
                txns = st.txns + txn,
            )
        }
        return txn
    }

    // ------------------------------------------------------------------ Requests

    fun request(requesterId: String, payerId: String, amount: Long, note: String, groupId: String? = null): String? {
        val s = _state.value
        val requester = s.account(requesterId) ?: return "Konto nicht gefunden."
        val payer = payerId.trim().uppercase()
        if (amount <= 0) return "Bitte einen Betrag eingeben."
        if (payer.isEmpty()) return "Bitte eine Person wählen."
        if (payer == requester.id) return "Du kannst nicht von dir selbst anfordern."
        val req = PayRequest(
            id = uid(), requester = requester.id, requesterName = requester.name,
            payer = payer, payerName = nameFor(requester.id, payer),
            amount = amount, note = note.trim(), groupId = groupId,
        )
        update { it.copy(requests = it.requests + req) }
        return null
    }

    fun payRequest(requestId: String): Result {
        val req = _state.value.requests.firstOrNull { it.id == requestId } ?: return Result.Error("Anfrage nicht gefunden.")
        if (req.status != RequestStatus.PENDING) return Result.Error("Anfrage ist nicht mehr offen.")
        val r = transfer(req.payer, req.requester, req.amount, req.note.ifEmpty { "Anfrage" }, Category.OTHER.key, TxnKind.REQUEST)
        if (r is Result.Ok) setRequestStatus(requestId, RequestStatus.PAID)
        return r
    }

    fun setRequestStatus(requestId: String, status: RequestStatus) = update { s ->
        s.copy(requests = s.requests.map { if (it.id == requestId) it.copy(status = status) else it })
    }

    /** For requests to accounts outside this device: the money arrived some other way. */
    fun markRequestPaidExternally(requestId: String) {
        val req = _state.value.requests.firstOrNull { it.id == requestId } ?: return
        if (req.status != RequestStatus.PENDING) return
        val txn = Txn(
            id = uid(), from = req.payer, to = req.requester, fromName = req.payerName, toName = req.requesterName,
            amount = req.amount, note = req.note.ifEmpty { "Anfrage" }, kind = TxnKind.REQUEST,
        )
        update { st ->
            st.copy(
                accounts = st.accounts.map { if (it.id == req.requester) it.copy(balance = it.balance + req.amount) else it },
                txns = st.txns + txn,
                requests = st.requests.map { if (it.id == requestId) it.copy(status = RequestStatus.PAID) else it },
            )
        }
    }

    fun newGroupId() = uid()

    // ------------------------------------------------------------------ Contacts

    fun saveContact(owner: String, accountId: String, name: String, favorite: Boolean = false, id: String? = null) = update { s ->
        val existing = s.contacts.firstOrNull { it.id == id }
            ?: s.contacts.firstOrNull { it.owner == owner && it.accountId.equals(accountId, true) }
        val c = Contact(
            id = existing?.id ?: uid(), owner = owner, accountId = accountId.trim().uppercase(),
            name = name.trim().ifEmpty { accountId.trim().uppercase() }, favorite = existing?.favorite ?: favorite,
            note = existing?.note ?: "",
        )
        s.copy(contacts = s.contacts.filterNot { it.id == c.id } + c)
    }

    fun toggleFavorite(contactId: String) = update { s ->
        s.copy(contacts = s.contacts.map { if (it.id == contactId) it.copy(favorite = !it.favorite) else it })
    }

    fun deleteContact(contactId: String) = update { s -> s.copy(contacts = s.contacts.filterNot { it.id == contactId }) }

    // ------------------------------------------------------------------ Goals

    fun createGoal(owner: String, name: String, emoji: String, target: Long, colorIndex: Int) = update { s ->
        s.copy(goals = s.goals + Goal(uid(), owner, name.trim(), emoji.ifBlank { "🎯" }, target, 0, colorIndex))
    }

    fun deleteGoal(goalId: String) {
        val g = _state.value.goals.firstOrNull { it.id == goalId } ?: return
        if (g.saved > 0) moveGoal(goalId, -g.saved)
        update { s -> s.copy(goals = s.goals.filterNot { it.id == goalId }) }
    }

    /** Positive = put money into the goal, negative = take it back out. */
    fun moveGoal(goalId: String, delta: Long): String? {
        val s = _state.value
        val g = s.goals.firstOrNull { it.id == goalId } ?: return "Sparziel nicht gefunden."
        val acc = s.account(g.owner) ?: return "Konto nicht gefunden."
        if (delta == 0L) return "Bitte einen Betrag eingeben."
        if (delta > 0 && delta > acc.balance) return "Nicht genug Guthaben."
        if (delta < 0 && -delta > g.saved) return "So viel ist im Sparziel nicht drin."
        val amount = kotlin.math.abs(delta)
        val txn = Txn(
            id = uid(),
            from = if (delta > 0) acc.id else null,
            to = if (delta > 0) null else acc.id,
            fromName = if (delta > 0) acc.name else "${g.emoji} ${g.name}",
            toName = if (delta > 0) "${g.emoji} ${g.name}" else acc.name,
            amount = amount,
            note = if (delta > 0) "Ins Sparziel" else "Aus dem Sparziel",
            kind = if (delta > 0) TxnKind.GOAL_IN else TxnKind.GOAL_OUT,
        )
        update { st ->
            st.copy(
                accounts = st.accounts.map { if (it.id == acc.id) it.copy(balance = it.balance - delta) else it },
                goals = st.goals.map { if (it.id == goalId) it.copy(saved = it.saved + delta) else it },
                txns = st.txns + txn,
            )
        }
        return null
    }

    // ------------------------------------------------------------------ Budgets

    fun setBudget(owner: String, category: String, limit: Long) = update { s ->
        val rest = s.budgets.filterNot { it.owner == owner && it.category == category }
        s.copy(budgets = if (limit > 0) rest + Budget(owner, category, limit) else rest)
    }

    // ------------------------------------------------------------------ Standing orders

    fun createRecurring(owner: String, toId: String, amount: Long, note: String, category: String, interval: Interval, firstRun: Long) = update { s ->
        val target = toId.trim().uppercase()
        s.copy(
            recurring = s.recurring + Recurring(
                id = uid(), owner = owner, toId = target, toName = nameFor(owner, target), amount = amount,
                note = note.trim(), category = category, interval = interval, nextRun = firstRun,
            ),
        )
    }

    fun toggleRecurring(id: String) = update { s ->
        s.copy(recurring = s.recurring.map { if (it.id == id) it.copy(active = !it.active) else it })
    }

    fun deleteRecurring(id: String) = update { s -> s.copy(recurring = s.recurring.filterNot { it.id == id }) }

    /** Runs every standing order that is due. Missed runs (app was closed) are caught up. */
    fun processRecurring(now: Long = System.currentTimeMillis()): Int {
        var executed = 0
        for (r in _state.value.recurring) {
            if (!r.active) continue
            var next = r.nextRun
            var runs = r.runs
            var guard = 0
            while (next <= now && guard++ < 60) {
                val res = transfer(r.owner, r.toId, r.amount, r.note.ifEmpty { "Dauerauftrag" }, r.category, TxnKind.RECURRING, r.toName)
                if (res !is Result.Ok) break
                executed++
                runs++
                next = if (r.interval == Interval.MONTHLY) Dates.addMonths(next, 1) else next + r.interval.days * Dates.DAY
            }
            if (next != r.nextRun) {
                update { s -> s.copy(recurring = s.recurring.map { if (it.id == r.id) it.copy(nextRun = next, runs = runs) else it }) }
            }
        }
        return executed
    }

    // ------------------------------------------------------------------ Import / export

    fun exportCsv(accountId: String): String {
        val s = _state.value
        val sb = StringBuilder("Datum;Typ;Von;An;Betrag;Kategorie;Notiz;ID\n")
        s.txns.filter { it.from == accountId || it.to == accountId }.sortedByDescending { it.time }.forEach { t ->
            val signed = if (t.to == accountId) t.amount else -t.amount
            fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
            sb.append(Dates.full(t.time)).append(';')
                .append(t.kind.name).append(';')
                .append(esc(t.fromName)).append(';')
                .append(esc(t.toName)).append(';')
                .append(Money.plain(signed)).append(';')
                .append(Category.of(t.category).label).append(';')
                .append(esc(t.note)).append(';')
                .append(t.id).append('\n')
        }
        return sb.toString()
    }

    fun exportBackup(): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(BankState.serializer(), _state.value)

    fun importBackup(text: String): Boolean {
        val parsed = runCatching { json.decodeFromString(BankState.serializer(), text) }.getOrNull() ?: return false
        update { parsed }
        _session.value = null
        return true
    }

    fun resetAll() {
        update { BankState() }
        _session.value = null
    }
}
