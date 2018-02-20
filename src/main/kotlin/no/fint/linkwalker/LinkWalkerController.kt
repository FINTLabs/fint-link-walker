package no.fint.linkwalker

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tests")
class LinkWalkerController {

    @GetMapping
    fun getText(): ResponseEntity<String> {
        return ResponseEntity.ok("testing")
    }

    @PostMapping
    fun startNewTest(@RequestBody testRequest: TestRequest): ResponseEntity<String> {
        return ResponseEntity.ok().build<String>()
    }

}