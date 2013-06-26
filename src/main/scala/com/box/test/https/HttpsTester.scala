package com.box.test.https

import javax.net.ssl._
import java.io._
import java.lang.String
import java.security.KeyStore
import org.apache.http.impl.conn.{BasicClientConnectionManager, PoolingClientConnectionManager}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.client.methods.{HttpRequestBase, HttpGet, HttpPost}
import org.apache.http.conn.scheme.Scheme
import org.apache.http.client.{ResponseHandler, HttpClient}
import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils
import scala.util.control.Exception._
import org.apache.http.entity.StringEntity
import com.ning.http.client.{Response, AsyncHttpClient, AsyncHttpClientConfig}
import scala.util.control._


object Main {
  def main(args: Array[String]) {
    new HttpsTester().main(args)
  }
}

class HttpsTester {

  def main(strings: Array[String]) = {

// create a Breaks object as follows
val loop = new Breaks

    val keyStore = "/box/etc/certs/client.p12"
    val keyStorePwd = "boxrocks"
    val keyPwd = "boxrocks"
    val trustStore = "/box/etc/certs/truststore"
    val trustStorePwd = "boxrocks"

    val sslConf = SSLConfig(keyStore, keyStorePwd, keyPwd, trustStore, trustStorePwd)
    val url = "https://lkantorovskiy-m.local"
    //val url = "https://job-manager01.dev.box.net"

    val httpClient = poolingHttpClient(10, Some(sslConf))

    loop.breakable {
      while (true) {

        // usage
        println
        println("[1] complete job")
        println("[2] complete job via Ning")
        println("[3] new job via Ning")
        println("[4] ping via Ning")
        println("[5] invalid (empty) post")
        println("[X] exit")
        println

        readLine("Enter number: ") match {
          case "1" =>
            executePostRequestTo(httpClient, url + ":8102/job/2cdda97d-bedf-48ba-9686-f4be2b65eb35/complete")
          case "2" =>
            executePostRequestTo(asyncClient(sslConf), url + ":8102/job/2cdda97d-bedf-48ba-9686-f4be2b65eb35/complete")
          case "3" =>
            executeNewJobRequest(url, asyncClient(sslConf))
          case "4" =>
            executeGetRequestTo(asyncClient(sslConf), url + "/ping") // requires apache to be running on 443
          case "5" =>
            executePostRequestTo(asyncClient(sslConf), url + ":8102")
          case _ =>
            loop.break
        }
      }
    }
  }

  def asyncClient(sslConf: SSLConfig) = {
    new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setSSLContext(createSSLContext(sslConf)).build)
  }

  def executeGetRequestTo(httpClient: HttpClient, to: String) = {
    val getRequest = new HttpGet(to)
    execute(httpClient, getRequest)
  }

  def executePostRequestTo(httpClient: HttpClient, to: String) = {
    val postRequest = new HttpPost(to)
    execute(httpClient, postRequest)
  }

  def executeNewJobRequest(url: String, httpClient: HttpClient) = {
    val postRequest = new HttpPost(url + ":8102/job")
    val msg = """[{"request_id":"request1","job_spec_set":[{"job_type":"test","queue":{"exchange":"conversion.lkantorovskiy.noop"},"params":{"file_id":123,"uniq_name":"Lev rules"}},{"job_type":"test","queue":{"exchange":"conversion.lkantorovskiy.noop"},"params":{"file_id":1234,"uniq_name":"Lev rules somewhat"}}]},{"request_id":"request2","job_spec_set":[{"job_type":"test","queue":{"exchange":"conversion.lkantorovskiy.noop"},"params":{"file_id":12345,"uniq_name":"Lev rules completely"}}]}]"""
    val header = "X-Content-Hash"
    postRequest.setHeader(header, "B9A3584D047EC5B6A89A197A4CB2602E170B5B73")
    postRequest.setEntity(new StringEntity(msg))
    httpClient.execute(postRequest, new ResponseHandler[Unit] {
      def handleResponse(response: HttpResponse) = {
        System.out.println("Received response: " + response)
        System.out.println("Received response body: " + EntityUtils.toString(response.getEntity))
      }
    })
  }

  def executeGetRequestTo(httpClient: AsyncHttpClient, to: String) = {
    val response = httpClient.prepareGet(to).execute().get()
    printResponse(response)
    httpClient.close()
  }

  def executePostRequestTo(httpClient: AsyncHttpClient, to: String) = {
    val response = httpClient.preparePost(to).execute().get()
    printResponse(response)
    httpClient.close()
  }

  def printResponse(response: Response) = {
    System.out.println("Received response code: " + response.getStatusCode)
    System.out.println("Received response body: " + response.getResponseBody)
  }

  def executeNewJobRequest(url: String, httpClient: AsyncHttpClient) = {
    val post = httpClient.preparePost(url + ":8102/job")
    val header = "X-Content-Hash"
    val msg = """[{"request_id":"request1","job_spec_set":[{"job_type":"test","queue":{"exchange":"conversion.lkantorovskiy.noop"},"params":{"file_id":123,"uniq_name":"Lev rules"}},{"job_type":"test","queue":{"exchange":"conversion.lkantorovskiy.noop"},"params":{"file_id":1234,"uniq_name":"Lev rules somewhat"}}]},{"request_id":"request2","job_spec_set":[{"job_type":"test","queue":{"exchange":"conversion.lkantorovskiy.noop"},"params":{"file_id":12345,"uniq_name":"Lev rules completely"}}]}]"""
    post.setBody(msg)
    post.addHeader(header, "B9A3584D047EC5B6A89A197A4CB2602E170B5B73")

    val futureResponse = post.execute()
    val response = futureResponse.get()
    System.out.println("Received response code: " + response.getStatusCode)
    System.out.println("Received response body: " + response.getResponseBody)
    httpClient.close()
  }

  private def execute(httpClient: HttpClient, request: HttpRequestBase) = {
    httpClient.execute(request, new ResponseHandler[Unit] {
      def handleResponse(response: HttpResponse) = {
        System.out.println("Received response: " + response)
        System.out.println("Received response body: " + EntityUtils.toString(response.getEntity))
      }
    })
  }

  def syncHttpClient(sslConfig: Option[SSLConfig]): HttpClient = {
    val httpClient = new DefaultHttpClient(new BasicClientConnectionManager)

    sslConfig.map(config => configureSSL(httpClient, config)).getOrElse(httpClient)
  }

  def poolingHttpClient(maxConnections: Int, sslConfig: Option[SSLConfig]): HttpClient = {
    val connectionManager = new PoolingClientConnectionManager
    connectionManager.setMaxTotal(maxConnections)
    val httpClient = new DefaultHttpClient(connectionManager)

    sslConfig.map(config => configureSSL(httpClient, config)).getOrElse(httpClient)
  }

  private def configureSSL(httpClient: DefaultHttpClient, config: SSLConfig) = {
    val socketFactory = new SSLSocketFactory(createSSLContext(config))
    val defaultPort = 443 // this does not matter, we use custom port
    val sch = new Scheme("https", defaultPort, socketFactory)
    httpClient.getConnectionManager().getSchemeRegistry().register(sch)

    httpClient
  }

  private def createSSLContext(sslConf: SSLConfig): SSLContext = {
    val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
    ctx.init(keyManagers(sslConf.keyStore, sslConf.keyPassword), trustManagers(sslConf.trustStore), null)
    ctx
  }

  private def keyManagers(keyStore: KeyStore, keyPassword: String): Array[KeyManager] = {
    val kmFactory = KeyManagerFactory.getInstance("SunX509")
    kmFactory.init(keyStore, keyPassword.toCharArray)
    kmFactory.getKeyManagers
  }

  private def trustManagers(trustStore: KeyStore): Array[TrustManager] = {
    val kmFactory = TrustManagerFactory.getInstance("SunX509")
    kmFactory.init(trustStore)
    kmFactory.getTrustManagers
  }
}

case class SSLConfig(
                      keyStoreFilePath: String,
                      keyStorePassword: String,
                      keyPassword: String,
                      trustStoreFilePath: String,
                      trustStorePassword: String) {

  lazy val keyStore: KeyStore = {
    // load the keystore
    val store = KeyStore.getInstance("pkcs12")
    val keyStoreInstream = new FileInputStream(new File(this.keyStoreFilePath))
    val keyStorePwd = this.keyStorePassword
    try {
      store.load(keyStoreInstream, keyStorePwd.toCharArray())
    } finally {
      ignoring(classOf[IOException]) {
        keyStoreInstream.close()
      }
    }

    store
  }

  lazy val trustStore: KeyStore = {
    // load the truststore
    val store = KeyStore.getInstance("jks")
    val trustStoreInstream = new FileInputStream(new File(this.trustStoreFilePath))
    try {
      store.load(trustStoreInstream, this.trustStorePassword.toCharArray())
    } finally {
      ignoring(classOf[IOException]) {
        trustStoreInstream.close()
      }
    }

    store
  }
}
