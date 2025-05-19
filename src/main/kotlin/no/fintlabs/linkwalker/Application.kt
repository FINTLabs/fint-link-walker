package no.fintlabs.linkwalker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FintCoreLinkWalkerApplication

fun main(args: Array<String>) {
	runApplication<FintCoreLinkWalkerApplication>(*args)
}
