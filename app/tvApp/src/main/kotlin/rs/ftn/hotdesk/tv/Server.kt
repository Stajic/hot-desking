package rs.ftn.hotdesk.tv

import rs.ftn.hotdesk.client.PublicApi
import rs.ftn.hotdesk.client.hotdeskHttpClient

/**
 * Veza sa serverom. Ekran koristi samo javne rute (lista sala, dnevna mreza), pa nema
 * prijave ni tokena; klijent i rute su isti kao u aplikaciji na telefonu (:app:shared).
 */
object Server {

    /**
     * Adresa servera; postavlja je [TvActivity]. Mora da se slaze sa
     * res/xml/network_security_config.xml, kao i na telefonu.
     */
    var baseUrl: String = "http://10.0.2.2:8080"

    val api = PublicApi(hotdeskHttpClient()) { baseUrl }
}
