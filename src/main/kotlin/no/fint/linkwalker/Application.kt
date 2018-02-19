package no.fint.linkwalker

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class FintLinkWalkerApplication

fun main(args: Array<String>) {
    SpringApplication.run(FintLinkWalkerApplication::class.java, *args)
}
