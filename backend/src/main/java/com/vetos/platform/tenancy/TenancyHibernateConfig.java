package com.vetos.platform.tenancy;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resolver'i Hibernate'e baglar. Spring Boot 3.5 bir
 * CurrentTenantIdentifierResolver bean'ini KENDILIGINDEN Hibernate'e
 * gecirmez (HibernateJpaConfiguration'da boyle bir ObjectProvider yok) --
 * bu customizer olmadan @TenantId filtresi hic devreye girmez.
 * @TenantId (discriminator tabanli multi-tenancy) icin ayrica bir
 * hibernate.multiTenancy ayari GEREKMEZ; o ayar yalnizca ayri
 * sema/veritabani modlari (MultiTenantConnectionProvider) icindir.
 */
@Configuration
class TenancyHibernateConfig {

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(TenantContextIdentifierResolver resolver) {
        return props -> props.put(org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
