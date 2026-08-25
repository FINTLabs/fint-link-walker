package no.novari.linkwalker.report.jpa

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EntityScan(basePackages = ["no.novari.linkwalker.report.jpa"])
@EnableJpaRepositories(basePackages = ["no.novari.linkwalker.report.jpa"])
class JpaReportConfig
