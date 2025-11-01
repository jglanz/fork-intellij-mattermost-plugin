@file:Suppress("UNCHECKED_CAST", "removal")

package at.dotti.mattermost

import at.dotti.intellij.plugins.team.mattermost.MMUserStatus
import at.dotti.intellij.plugins.team.mattermost.model.*
import at.dotti.intellij.plugins.team.mattermost.model.Channel.ChannelData
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.ui.SortedListModel

import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.DefaultClientConnectionReuseStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.java_websocket.WebSocketImpl
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.net.URISyntaxException
import java.security.*
import java.security.cert.CertificateException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.function.BiConsumer
import java.util.function.Consumer
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.swing.ListModel
import javax.swing.SwingUtilities

class MattermostClient {
    private var MM_URL: String? = null

    private val client: CloseableHttpClient

    private var token: String? = null

    private var user: User? = null

    private var users: MutableMap<String, MutableMap<String, JsonElement>> = mutableMapOf()

    private var status: MutableMap<String, String> = mutableMapOf()

    private var listModel: SortedListModel<MMUserStatus?>? = null

    private var balloonCallback: Consumer<String?>? = null

    private var statusCallback: Consumer<String?>? = null

    private var chatCallback: BiConsumer<PostedData?, Users?>? = null

    private var teams: Array<TeamMember?> = arrayOf()

    private val gson: Gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    @Throws(IOException::class, URISyntaxException::class)
    fun login(username: String, password: String) {
        val req = HttpPost(url(USERS_LOGIN_URL))
        req.addHeader("Content-Type", "application/json")
        val body = JsonObject().apply {
            addProperty("login_id", username)
            addProperty("password", password)
        }
        req.entity =  StringEntity(body.toString())
        val resp = this.client.execute(req)
        println(resp.entity)
        this.user = gson.fromJson<User>(IOUtils.toString(resp.entity.content, "UTF-8"), User::class.java)
        this.token = resp.getFirstHeader("Token").value
        //            resp.getFirstHeader("Set-Cookie").value.replace("(.*MMAUTHTOKEN=)([^;]+)(;.*)".toRegex(), "$2")
        status("logged on as: " + this.user!!.username)
    }

    private fun status(s: String?) {
        if (this.statusCallback != null) {
            this.statusCallback!!.accept(s)
        }
    }

    @Throws(URISyntaxException::class)
    private fun url(apiUrl: String?): URI {
        if (!MM_URL!!.endsWith("/")) {
            MM_URL += "/"
        }
        return URI(MM_URL + apiUrl)
    }

    @Throws(URISyntaxException::class)
    private fun wss(apiUrl: String?): URI {
        if (!MM_URL!!.endsWith("/")) {
            MM_URL += "/"
        }
        val wsUrl = if (MM_URL!!.startsWith("https"))
            MM_URL!!.replace("https", "wss")
        else
            MM_URL!!.replace("http", "ws")
        return URI(wsUrl + apiUrl)
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun users() {
        val req = HttpGet(url(USERS_URL))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val resp = this.client.execute(req)
        val userJsons = JsonParser.parseReader(InputStreamReader(resp.entity.content, "UTF-8"))
        val userJsonsArray:List<MutableMap<String, JsonElement>> = userJsons.asJsonArray
            .filter { it.isJsonObject }
            .map { it.asJsonObject.asMap().toMutableMap() }
        this.users =
            userJsonsArray.associateBy { it -> it["id"]!!.asString }.toMutableMap()

        resp.close()
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun user() {
        val req = HttpGet(url(String.format(USERS_ID_URL, this.user!!.id)))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val resp = this.client.execute(req)
        val user = gson.fromJson<MutableMap<*, *>?>(
            IOUtils.toString(resp.entity.content, "UTF-8"),
            MutableMap::class.java
        )
        println(user)
        resp.close()
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun teams() {
        this.teams = arrayOf<TeamMember?>()
//        val req = HttpGet(url(TEAMS_URL))
//        req.addHeader("Content-Type", "application/json")
//        req.addHeader("Authorization", "Bearer " + this.token)
//        val resp = this.client.execute(req)
//        val json = IOUtils.toString(resp.entity.content, "UTF-8")
//        this.teams = gson.fromJson<Array<TeamMember?>?>(json, Array<TeamMember>::class.java)
//        resp.close()
    }

    @Throws(URISyntaxException::class, IOException::class)
    private fun createChannel(s: String?): ChannelData? {
        val req = HttpPost(url(String.format(CHANNEL_CREATE_URL, this.teams[0]!!.teamId)))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val ids: MutableList<String?> = ArrayList<String?>()
        ids.add(this.user!!.id)
        ids.add(s)
        val jsonReq = Gson()
        req.entity = StringEntity(jsonReq.toJson(ids))
        val resp = this.client.execute(req)
        val json = IOUtils.toString(resp.entity.content, "UTF-8")
        println(json)
        return gson.fromJson<ChannelData?>(json, ChannelData::class.java)
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun channels(): Channels {
        val req = HttpGet(url(String.format(CHANNELS_URL, this.teams[0]!!.teamId)))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val resp = this.client.execute(req)
        val json = IOUtils.toString(resp.entity.content, "UTF-8")
        println(json)
        val channelData = gson.fromJson<Channels>(json, Channels::class.java)
        resp.close()
        return channelData
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun userStatus() {
        val req = HttpPost(url(USERS_STATUS_IDS_URL))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val array = ArrayList<String?>()
        for (o in this.users.entries) {
            val map = o as MutableMap.MutableEntry<*, *>
            array.add((map.value as MutableMap<*, *>).get("id") as String?)
        }
        val jsonReq = Gson()
        req.entity = StringEntity(jsonReq.toJson(array))
        val resp = this.client.execute(req)
        val json = IOUtils.toString(resp.entity.content, "UTF-8")
        println(json)
        this.status = gson.fromJson<MutableMap<*, *>>(json, MutableMap::class.java) as MutableMap<String, String>
        resp.close()
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun posts(id: String?): MutableMap<*, *>? {
        val req = HttpGet(url(String.format(CHANNEL_POSTS_URL, this.user!!.id, id)))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val resp = this.client.execute(req)
        val posts = gson.fromJson<MutableMap<*, *>?>(
            IOUtils.toString(resp.entity.content, "UTF-8"),
            MutableMap::class.java
        )
        println("posts")
        println(posts)
        resp.close()
        return posts
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun channelById(id: String?): Channel? {
        val req = HttpGet(url(String.format(CHANNEL_BY_ID_URL, this.teams[0]!!.teamId, id)))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val resp = this.client.execute(req)
        val channel =
            gson.fromJson<Channel?>(IOUtils.toString(resp.entity.content, "UTF-8"), Channel::class.java)
        resp.close()
        return channel
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun channelMembersIds(id: String?): Users? {
        val req = HttpGet(url(String.format(CHANNEL_MEMBERS_IDS_URL, this.teams[0]!!.teamId)))
        //		req.setEntity(new StringEntity("['"+this.user.getId()+"']"));
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val resp = this.client.execute(req)
        try {
            val msg = IOUtils.toString(resp.entity.content, "UTF-8")
            println(msg)
            resp.close()
            return gson.fromJson<Users?>(msg, Users::class.java)
        } catch (e: JsonSyntaxException) {
            return Users()
        }
    }

    private var ws: WebSocketClient? = null

    private var seq = 1

    private var statusSeq = -1

    init {
        val connManager = PoolingHttpClientConnectionManager()
        connManager.defaultMaxPerRoute = 5
        connManager.maxTotal = 5

        client = HttpClients.custom()
            .setConnectionManager(connManager)
            .setConnectionManagerShared(true)
            .setConnectionReuseStrategy(DefaultClientConnectionReuseStrategy.INSTANCE)
            .build()
    }

    @Throws(
        IOException::class,
        URISyntaxException::class,
        CertificateException::class,
        InterruptedException::class,
        UnrecoverableKeyException::class,
        NoSuchAlgorithmException::class,
        KeyStoreException::class,
        KeyManagementException::class
    )
    fun run(listModel: SortedListModel<MMUserStatus?>, username: String, password: String, url: String) {
        MM_URL = url
        login(username, password)
        users()
        teams()
        userStatus()
        ws = websocket(listModel)
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    if (ws == null || ws!!.isClosed) {
                        Notifications.Bus.notify(
                            Notification(
                                "team",
                                "mattermost websocket",
                                "websocket reconnecting...",
                                NotificationType.INFORMATION
                            )
                        )
                        ws = websocket(listModel)
                    }
                    ws!!.send("{\"action\":\"get_statuses\",\"seq\":" + (++seq) + "}")
                    statusSeq = seq
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Notifications.Bus.notify(
                        Notification(
                            "team",
                            "mattermost Error",
                            t.message!!,
                            NotificationType.ERROR
                        )
                    )
                }
            }
        }, 5000, 60000)
        this.listModel = listModel
        fillListModel()
    }

    private fun fillListModel(data: MutableMap<String, String>) {
        SwingUtilities.invokeLater(Runnable {
            for (s in data.keys) {
                val status: String = data[s]!!
                val username = this.users[s]!!["username"] as String?
                val userStatus = MMUserStatus(s, username, status.equals("online", ignoreCase = true))
                val iter = listModel!!.getItems().iterator()
                while (iter.hasNext()) {
                    val mmUserStatus = iter.next()
                    if (mmUserStatus?.username() == userStatus.username()) {
                        iter.remove()
                        break
                    }
                }
                listModel!!.add(userStatus)
            }
        })
    }

    private fun fillListModel() {
        listModel!!.clear()
        for (s in this.status!!.keys) {
            val status: String = this.status!!.get(s)!!
            val username = this.users!!.get(s)!!.get("username") as String?
            val userStatus = MMUserStatus(s, username, status.equals("online", ignoreCase = true))
            listModel!!.add(userStatus)
        }
    }

    @Throws(
        URISyntaxException::class,
        IOException::class,
        KeyStoreException::class,
        CertificateException::class,
        NoSuchAlgorithmException::class,
        UnrecoverableKeyException::class,
        KeyManagementException::class,
        InterruptedException::class
    )
    private fun websocket(list: ListModel<MMUserStatus?>?): WebSocketClient {
        WebSocketImpl.DEBUG = false

        val connectionOpenLatch = CountDownLatch(1)
        val ws: WebSocketClient = object : WebSocketClient(wss(WEBSOCKET_URL), Draft_6455()) {
            override fun onOpen(serverHandshake: ServerHandshake) {
                println(serverHandshake.httpStatusMessage)
                connectionOpenLatch.countDown()

                val json =
                    "{\"seq\":1,\"action\":\"authentication_challenge\",\"data\":{\"token\":\"" + token + "\"}}"
                send(json)
            }

            override fun onClosing(i: Int, reason: String?, remote: Boolean) {
                Notifications.Bus.notify(
                    Notification(
                        "team",
                        "mattermost closing",
                        "mattermost closing: code = " + i + ", reason = " + reason + ", remote = " + remote,
                        NotificationType.INFORMATION
                    )
                )
                println("closing: code = " + i + ", reason = " + reason + ", remote = " + remote)
            }

            override fun onMessage(s: String) {
                try {
                    val g = Gson()
                    val map: MutableMap<String?, Any?> = g.fromJson<MutableMap<*, *>>(s, MutableMap::class.java) as MutableMap<String?, Any?>
                    if (map.containsKey("seq_reply")) {
                        // got a response to my request
                        if (map.get("seq_reply") as Double == statusSeq.toDouble()) {
                            val status = map.get("status") as String
                            when (status) {
                                "OK" -> if (map.containsKey("data")) {
                                    userStatus()
                                    fillListModel(this@MattermostClient.status)
                                }

                                "FAIL" -> SwingUtilities.invokeLater(Runnable {
                                    val gson = GsonBuilder()
                                    val json = gson.setPrettyPrinting().create()
                                    val text = StringBuilder()
                                    text.append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                    text.append("\n")
                                    text.append(json.toJson(map.get("error")))
                                    text.append("\n---\n")
                                    status(text.toString())
                                })
                            }
                        }
                    } else if (map.containsKey("event")) {
                        // got an event
                        val event = map.get("event") as String
                        val data = map.get("data") as MutableMap<String?, Any?>?
                        val broadcast = map.get("broadcast") as MutableMap<String?, Any?>?

                        val gson =
                            GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
                        when (event) {
                            "status_change" -> balloonCallback!!.accept("status changed: " + data)
                            "typing" -> {
                                val typing = gson.fromJson<Typing>(s, Typing::class.java)
                                balloonCallback!!.accept(
                                    users!!.get(typing.data.userId)!!.get("username")
                                        .toString() + " is typing a message ..."
                                )
                            }

                            "posted" -> try {
                                val posted = gson.fromJson<Posted>(s, Posted::class.java)
                                write(posted.data, channelMembersIds(posted.data.post.channelId))
                            } catch (e: JsonSyntaxException) {
                                val posted = gson.fromJson<PostedWithString>(s, PostedWithString::class.java)
                                val postString = posted.data.post
                                val post = gson.fromJson<Post>(postString, Post::class.java)
                                val pd = PostedData()
                                pd.channelDisplayName = posted.data.channelDisplayName
                                pd.channelName = posted.data.channelName
                                pd.channelType = posted.data.channelType
                                pd.mentions = gson.fromJson<MutableList<String>?>(
                                    posted.data.mentions,
                                    MutableList::class.java
                                )
                                pd.post = post
                                pd.senderName = posted.data.senderName
                                pd.teamId = posted.data.teamId
                                write(pd, channelMembersIds(post.channelId))
                            }

                            "hello" -> balloonCallback!!.accept("Welcome! You are connected now!")
                            "channel_viewed" -> {}
                            else -> {
                                println("msg: " + s)
                                Notifications.Bus.notify(
                                    Notification(
                                        "mattermost",
                                        event,
                                        s,
                                        NotificationType.INFORMATION
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    val text = StringBuilder()
                    text.append(s)
                    text.append("\n")
                    text.append(e.message)
                    Notifications.Bus.notify(
                        Notification(
                            "mattermost",
                            text.toString(),
                            s,
                            NotificationType.INFORMATION
                        )
                    )
                }
            }

            override fun onClose(i: Int, s: String?, b: Boolean) {
                println(s)
            }

            override fun onError(e: Exception) {
                e.printStackTrace()
                Notifications.Bus.notify(
                    Notification(
                        "team",
                        "Mattermost Connection error",
                        e.message!!,
                        NotificationType.INFORMATION
                    )
                )
                connectionOpenLatch.countDown()
            }
        }

        if (MM_URL!!.startsWith("https")) {
            val factory = createSslSocketFactory()
            ws.setSocket(factory.createSocket())
        }

        val wsth = Thread(ws)
        wsth.setName("WebsocketReadThread")
        wsth.setDaemon(true)
        wsth.start()
        connectionOpenLatch.await()
        return ws
    }

    @Throws(
        KeyStoreException::class,
        IOException::class,
        NoSuchAlgorithmException::class,
        CertificateException::class,
        UnrecoverableKeyException::class,
        KeyManagementException::class
    )
    private fun createSslSocketFactory(): SSLSocketFactory {
        // load up the key store
        val STORETYPE = "JKS"
        val KEYSTORE = "keystore.jks"
        val STOREPASSWORD = "storepassword"
        val KEYPASSWORD = "keypassword"

        val ks = KeyStore.getInstance(STORETYPE)
        ks.load(MattermostClient::class.java.getResourceAsStream(KEYSTORE), STOREPASSWORD.toCharArray())

        val kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(ks, KEYPASSWORD.toCharArray())
        val tmf = TrustManagerFactory.getInstance("SunX509")
        tmf.init(ks)

        var sslContext: SSLContext? = null
        sslContext = SSLContext.getInstance("TLS")
        //		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        sslContext.init(
            null,
            null,
            null
        ) // will use java's default key and trust store which is sufficient unless you deal with self-signed certificates

        return sslContext.socketFactory // (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun compose(text: String?, channelId: String?): Post? {
        val post = Post()
        @Suppress("removal")
        post.message = StringEscapeUtils.escapeHtml(text)
        post.channelId = channelId
        post.userId = this.user!!.id
        return createPost(post)
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun createPost(post: Post): Post? {
        val req = HttpPost(url(String.format(CREATE_POST_URL, this.teams[0]!!.teamId, post.channelId)))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        req.entity = StringEntity(gson.toJson(post))
        val resp = this.client.execute(req)
        val json = gson.fromJson<Post?>(IOUtils.toString(resp.entity.content, "UTF-8"), Post::class.java)
        resp.close()
        return json
    }

    private fun write(post: PostedData?, channel: Users?) {
        if (this.chatCallback != null) {
            this.chatCallback!!.accept(post, channel)
        }
    }

    fun setChatCallback(chatCallback: BiConsumer<PostedData?, Users?>?) {
        this.chatCallback = chatCallback
    }

    fun setStatusCallback(statusCallback: Consumer<String?>?) {
        this.statusCallback = statusCallback
    }

    fun setBalloonCallback(balloonCallback: Consumer<String?>) {
        this.balloonCallback = balloonCallback
    }

    fun getBalloonCallback(): Consumer<String?> {
        return balloonCallback!!
    }

    fun getUser(): User {
        return user!!
    }

    fun getUsers(): MutableMap<String, MutableMap<String, JsonElement>> {
        return users
    }

    @Throws(URISyntaxException::class, IOException::class)
    fun view(channelId: String?): MutableMap<*, *>? {
        val req = HttpPost(url(String.format(VIEW_CHANNEL, this.teams[0]!!.teamId)))
        req.addHeader("Content-Type", "application/json")
        req.addHeader("Authorization", "Bearer " + this.token)
        val map: MutableMap<String?, String?> = HashMap<String?, String?>()
        map.put("channel_id", channelId)
        map.put("prev_channel_id", "")
        req.entity = StringEntity(gson.toJson(map))
        val resp = this.client.execute(req)
        val json = gson.fromJson<MutableMap<*, *>?>(
            IOUtils.toString(resp.entity.content, "UTF-8"),
            MutableMap::class.java
        )
        resp.close()
        return json
    }

    @Throws(IOException::class, URISyntaxException::class)
    fun createChat(s: String): ChannelData? {
        val channels = channels()
        val channel = channels.stream().filter { o: ChannelData? -> o!!.name.endsWith(s) }.findFirst()
        if (channel != null && channel.isPresent) {
            // found
            return channel.get()
        }
        return createChannel(s)
    }

    companion object {
        @Deprecated("use v4 instead")
        private const val API = "api/v4"

        private const val API_V4 = "api/v4"

        private val USERS_LOGIN_URL: String = API + "/users/login"

        private val USERS_URL: String = API + "/users?page=0&per_page=100"

        private val USERS_ID_URL: String = API + "/users/%s"

        private val TEAMS_URL: String = API + "/teams/members"

        private val CHANNELS_URL: String = API + "/teams/%s/channels/"

        private val CHANNEL_CREATE_URL: String = API_V4 + "/channels/direct"

        private val CHANNEL_POSTS_URL: String = API + "/users/%s/channels/%s/unread"

        private val CHANNEL_BY_ID_URL: String = API + "/teams/%s/channels/%s/"

        private val CHANNEL_MEMBERS_IDS_URL: String = API + "/teams/%s/channels/users/0/20"

        private val USERS_STATUS_IDS_URL: String = API + "/users/status/ids"

        private val WEBSOCKET_URL: String = API + "/users/websocket"

        private val CREATE_POST_URL: String = API + "/teams/%s/channels/%s/posts/create"

        private val VIEW_CHANNEL: String = API + "/teams/%s/channels/view"
    }
}
