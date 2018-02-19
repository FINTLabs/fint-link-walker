package no.fint.linkwalker

import org.amshove.kluent.`should equal`
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class ApplicationSpec: Spek({
    describe("an application") {
        val application = Application()

        it("should run a test") {
            "hello" `should equal` "hello"
        }
    }

})