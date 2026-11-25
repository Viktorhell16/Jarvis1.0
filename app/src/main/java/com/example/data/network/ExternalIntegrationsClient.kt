package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GitHubVerificationResult(
    val username: String,
    val publicRepos: Int,
    val bio: String
)

class ExternalIntegrationsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun verifyGitHubPat(pat: String): Result<GitHubVerificationResult> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/user")
                .addHeader("Authorization", "Bearer ${pat.trim()}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("GitHub HTTP ${resp.code}: ${body.take(160)}"))
                }
                val obj = JSONObject(body)
                Result.success(
                    GitHubVerificationResult(
                        username = obj.optString("login", "github_user"),
                        publicRepos = obj.optInt("public_repos", 0),
                        bio = obj.optString("bio", "")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGitHubContext(pat: String, query: String): String = withContext(Dispatchers.IO) {
        if (pat.isBlank()) return@withContext ""
        return@withContext runCatching {
            val encodedQ = URLEncoder.encode(query.take(60), "UTF-8")
            val repoReq = Request.Builder()
                .url("https://api.github.com/user/repos?sort=updated&per_page=6")
                .addHeader("Authorization", "Bearer $pat")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()
            val reposList = mutableListOf<String>()
            client.newCall(repoReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string() ?: "[]")
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        reposList.add("${r.optString("full_name")} (★${r.optInt("stargazers_count")}, ${r.optString("language", "text")})")
                    }
                }
            }
            val searchReq = Request.Builder()
                .url("https://api.github.com/search/repositories?q=$encodedQ&per_page=3")
                .addHeader("Authorization", "Bearer $pat")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()
            val searchList = mutableListOf<String>()
            client.newCall(searchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val items = JSONObject(resp.body?.string() ?: "{}").optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val r = items.getJSONObject(i)
                        searchList.add("${r.optString("full_name")}: ${r.optString("description", "").take(90)}")
                    }
                }
            }
            buildString {
                if (reposList.isNotEmpty()) append("[GitHub User Repos]: ${reposList.joinToString("; ")}\n")
                if (searchList.isNotEmpty()) append("[GitHub Search]: ${searchList.joinToString("; ")}")
            }
        }.getOrDefault("")
    }

    suspend fun fetchGmailContext(oauthToken: String): String = withContext(Dispatchers.IO) {
        if (oauthToken.isBlank()) return@withContext ""
        return@withContext runCatching {
            val listReq = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=5")
                .addHeader("Authorization", "Bearer $oauthToken")
                .get()
                .build()
            val snippets = mutableListOf<String>()
            client.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val msgs = JSONObject(resp.body?.string() ?: "{}").optJSONArray("messages") ?: JSONArray()
                for (i in 0 until minOf(msgs.length(), 4)) {
                    val id = msgs.getJSONObject(i).optString("id")
                    val detailReq = Request.Builder()
                        .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=Subject&metadataHeaders=From")
                        .addHeader("Authorization", "Bearer $oauthToken")
                        .get()
                        .build()
                    client.newCall(detailReq).execute().use { dResp ->
                        if (dResp.isSuccessful) {
                            val dObj = JSONObject(dResp.body?.string() ?: "{}")
                            val headers = dObj.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
                            var subj = ""
                            var from = ""
                            for (h in 0 until headers.length()) {
                                val hObj = headers.getJSONObject(h)
                                when (hObj.optString("name")) {
                                    "Subject" -> subj = hObj.optString("value")
                                    "From" -> from = hObj.optString("value")
                                }
                            }
                            snippets.add("From $from | Subject: $subj | ${dObj.optString("snippet", "").take(80)}")
                        }
                    }
                }
            }
            if (snippets.isEmpty()) "" else "[Gmail Recent]: ${snippets.joinToString(" || ")}"
        }.getOrDefault("")
    }

    suspend fun fetchDriveContext(oauthToken: String): String = withContext(Dispatchers.IO) {
        if (oauthToken.isBlank()) return@withContext ""
        return@withContext runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?pageSize=8&fields=files(id,name,mimeType,modifiedTime)")
                .addHeader("Authorization", "Bearer $oauthToken")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val files = JSONObject(resp.body?.string() ?: "{}").optJSONArray("files") ?: JSONArray()
                val items = (0 until files.length()).map { i ->
                    val f = files.getJSONObject(i)
                    "${f.optString("name")} (${f.optString("mimeType").substringAfterLast('.')})"
                }
                if (items.isEmpty()) "" else "[Google Drive Files]: ${items.joinToString(", ")}"
            }
        }.getOrDefault("")
    }

    suspend fun fetchCalendarContext(oauthToken: String): String = withContext(Dispatchers.IO) {
        if (oauthToken.isBlank()) return@withContext ""
        return@withContext runCatching {
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val req = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=6&singleEvents=true&orderBy=startTime&timeMin=$nowIso")
                .addHeader("Authorization", "Bearer $oauthToken")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val events = JSONObject(resp.body?.string() ?: "{}").optJSONArray("items") ?: JSONArray()
                val items = (0 until events.length()).map { i ->
                    val ev = events.getJSONObject(i)
                    val start = ev.optJSONObject("start")?.let { it.optString("dateTime").ifBlank { it.optString("date") } } ?: ""
                    "${ev.optString("summary", "Event")} @ $start"
                }
                if (items.isEmpty()) "" else "[Google Calendar Upcoming]: ${items.joinToString("; ")}"
            }
        }.getOrDefault("")
    }

    suspend fun fetchGenericBearerApi(endpoint: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (endpoint.isBlank()) return@withContext Result.failure(IllegalArgumentException("Endpoint URL is blank"))
            val builder = Request.Builder().url(endpoint.trim()).get()
            if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer ${token.trim()}")
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.isSuccessful) Result.success(text.take(1200))
                else Result.failure(IllegalStateException("HTTP ${resp.code}: ${text.take(180)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
