package no.fintlabs.linkwalker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FintCoreLinkWalkerApplication

fun main(args: Array<String>) {
	runApplication<FintCoreLinkWalkerApplication>(*args)
}
