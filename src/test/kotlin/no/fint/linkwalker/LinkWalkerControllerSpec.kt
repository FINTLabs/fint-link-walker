package no.fint.linkwalker

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class LinkWalkerControllerSpec : Spek({
    describe("LinkWalkerController") {
        val controller = LinkWalkerController()
        val mockMvc = standaloneSetup(controller).build()

        it("GET /tests") {
            mockMvc.perform(get("/tests"))
                    .andDo(print())
                    .andExpect(status().isOk)
        }
    }
})