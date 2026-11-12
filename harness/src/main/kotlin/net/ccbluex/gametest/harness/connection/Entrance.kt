package net.ccbluex.gametest.harness.connection

/** How a test's client gets into the world of its session and out again, whichever server holds the session. */
internal interface Entrance {

    /** Gets the client onto the server; [resume] when it comes back into the session it left. */
    fun connect(resume: Boolean)

    /** Opens the session once the client is on the server, or takes it up again when [resume]. */
    fun open(resume: Boolean)

    /** Ends the session while the client is still in it. */
    fun end()

    /** Has the server disconnect the client the way a stopping server does. */
    fun drop()

    /** Back to the title screen, like a player who leaves. */
    fun leave()

}
