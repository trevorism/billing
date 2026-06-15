package com.trevorism.factory

import com.trevorism.ClasspathBasedPropertiesProvider
import com.trevorism.PropertiesProvider
import com.trevorism.data.PingingDatastoreRepository
import com.trevorism.data.Repository
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient
import com.trevorism.model.Customer
import com.trevorism.model.PaymentMethod
import com.trevorism.model.Transaction
import com.trevorism.model.Vendor
import com.trevorism.payment.CardanoPaymentProvider
import com.trevorism.payment.XrpPaymentProvider
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

/**
 * Wires the trevorism platform clients (authenticated HTTP, secrets, datastore repositories) into the
 * Micronaut application context so they can be injected by providers, services, and controllers.
 */
@Factory
class BeanFactory {

    /**
     * App-credentialed HTTP client. It obtains its own bearer token from the clientId/clientSecret in
     * secrets.properties, used for service-to-service calls (e.g. to the Stripe service).
     */
    @Singleton
    SecureHttpClient secureHttpClient() {
        return new AppClientSecureHttpClient()
    }

    @Singleton
    PropertiesProvider propertiesProvider() {
        return new ClasspathBasedPropertiesProvider()
    }

    @Singleton
    @Bean
    Repository<Customer> customerRepository() {
        return new PingingDatastoreRepository<>(Customer)
    }

    @Singleton
    @Bean
    Repository<Vendor> vendorRepository() {
        return new PingingDatastoreRepository<>(Vendor)
    }

    @Singleton
    @Bean
    Repository<Transaction> transactionRepository() {
        return new PingingDatastoreRepository<>(Transaction)
    }

    @Singleton
    @Bean
    Repository<PaymentMethod> paymentMethodRepository() {
        return new PingingDatastoreRepository<>(PaymentMethod)
    }

    // One crypto provider per network, all live at once. The registry collects them via List<PaymentProvider>
    // and routes by their network-qualified name (xrp-testnet, xrp-mainnet, cardano-preprod, cardano-mainnet).

    @Singleton
    @Bean
    XrpPaymentProvider xrpTestnetProvider(@Value('${xrp.testnet.rpcUrl}') String rpcUrl) {
        return new XrpPaymentProvider("xrp-testnet", "testnet", rpcUrl)
    }

    @Singleton
    @Bean
    XrpPaymentProvider xrpMainnetProvider(@Value('${xrp.mainnet.rpcUrl}') String rpcUrl) {
        return new XrpPaymentProvider("xrp-mainnet", "mainnet", rpcUrl)
    }

    @Singleton
    @Bean
    CardanoPaymentProvider cardanoPreprodProvider(PropertiesProvider propertiesProvider,
                                                  @Value('${cardano.backend:koios}') String backend) {
        return new CardanoPaymentProvider(propertiesProvider, "cardano-preprod", "preprod", backend)
    }

    @Singleton
    @Bean
    CardanoPaymentProvider cardanoMainnetProvider(PropertiesProvider propertiesProvider,
                                                  @Value('${cardano.backend:koios}') String backend) {
        return new CardanoPaymentProvider(propertiesProvider, "cardano-mainnet", "mainnet", backend)
    }
}
