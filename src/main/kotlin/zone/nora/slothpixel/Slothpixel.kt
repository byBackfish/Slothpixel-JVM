/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2020, Nora Cos <https://github.com/mew>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/* The above notice applies to all files in this repository. */
package zone.nora.slothpixel

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import zone.nora.slothpixel.player.achievements.Achievements
import zone.nora.slothpixel.bans.Bans
import zone.nora.slothpixel.guild.Guild
import zone.nora.slothpixel.health.Health
import zone.nora.slothpixel.player.Player
import zone.nora.slothpixel.skyblock.auctions.PastSkyblockAuctions
import zone.nora.slothpixel.skyblock.auctions.SkyblockAuction
import zone.nora.slothpixel.skyblock.profiles.SimpleSkyblockProfile
import zone.nora.slothpixel.skyblock.profiles.SkyblockProfile
import zone.nora.slothpixel.util.JsonUtil.convertToJsonArray
import zone.nora.slothpixel.util.TimeUtil
import zone.nora.slothpixel.util.exceptions.SlothpixelApiException
import zone.nora.slothpixel.util.exceptions.impl.InvalidPlayerException
import zone.nora.slothpixel.util.exceptions.impl.ProfileNotFoundException
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.net.URLConnection
import java.util.*

/*
 * Created by Nora Cos on 14/02/20.
 * Last modified 16/02/20.
 */
class Slothpixel {
    private val url = "https://api.slothpixel.me/api"

    fun getPlayer(nameOrUUID: String): Player {
        val name = nameOrUUID.replace("-", "")
        val gson = Gson()
        val jsonUrl = "$url/players/$name"
        val json = getFromUrl(jsonUrl)
        return gson.fromJson<Player>(json, Player::class.java)
    }

    fun getPlayer(uuid: UUID): Player = getPlayer(uuid.toString())

    fun getPlayerAchievements(nameOrUUID: String): Achievements {
        val name = nameOrUUID.replace("-", "")
        val gson = Gson()
        val jsonUrl = "$url/players/$name/achievements"
        val json = getFromUrl(jsonUrl)
        return gson.fromJson<Achievements>(json, Achievements::class.java)
    }

    /**
     * Returns Guild information of a player via their username
     * or UUID.
     *
     * @param playerNameOrUUID Username or UUID of a player.
     * @return Guild Object of the players guild.
     */
    fun getGuild(playerNameOrUUID: String): Guild {
        val player = playerNameOrUUID.replace("-", "")
        val gson = Gson()
        val jsonUrl = "$url/guilds/$player?populatePlayers=true"
        val json = try { readJsonUrl(jsonUrl) } catch (ex: FileNotFoundException) { throw InvalidPlayerException()
        }
        return gson.fromJson<Guild>(json, Guild::class.java)
    }

    fun getGuild(playerUUID: UUID): Guild = getGuild(playerUUID.toString())

    fun getBans(): Bans {
        val gson = Gson()
        val jsonUrl = "$url/bans"
        val json = getFromUrl(jsonUrl)
        return gson.fromJson<Bans>(json, Bans::class.java)
    }

    fun getSkyblockProfiles(playerNameOrUUID: String): Array<SimpleSkyblockProfile> {
        val player = playerNameOrUUID.replace("-", "")
        val jsonUrl = "$url/skyblock/profiles/$player"
        val json = getFromUrl(jsonUrl)
        return Gson().fromJson(convertToJsonArray(json), Array<SimpleSkyblockProfile>::class.java)
    }

    @JvmOverloads
    fun getSkyblockProfile(playerNameOrUUID: String, profileId: String = ""): SkyblockProfile {
        val player = playerNameOrUUID.replace("-", "")
        val jsonUrl = "$url/skyblock/profile/$player/$profileId"
        val json = getFromUrl(jsonUrl)
        return Gson().fromJson<SkyblockProfile>(json, SkyblockProfile::class.java)
    }

    @JvmOverloads
    fun getSkyblockAuctions(limit: Int = 100, id: String = "", auctionUUID: String = "", itemUUID: String = ""): Array<SkyblockAuction> {
        var jsonUrl = "$url/skyblock/auctions?limit=$limit"
        if (id != "") jsonUrl += "&id=$id"
        if (auctionUUID != "") jsonUrl += "&auctionUUID=$auctionUUID"
        if (itemUUID != "") jsonUrl += "&itemUUID=$itemUUID"
        val json = JsonParser().parse(getPage(jsonUrl)).asJsonArray
        return Gson().fromJson(json, Array<SkyblockAuction>::class.java)
    }

    @JvmOverloads
    fun getPastSkyblockAuctions(itemId: String, from: Long = TimeUtil.yesterday(), to: Long = TimeUtil.now()): PastSkyblockAuctions {
        val jsonUrl = "$url/skyblock/auctions/$itemId?from=$from&to=$to"
        val json = getFromUrl(jsonUrl)
        return Gson().fromJson<PastSkyblockAuctions>(json, PastSkyblockAuctions::class.java)
    }

    fun getHealth(): Health {
        val gson = Gson()
        val jsonUrl = "$url/health"
        val json = getFromUrl(jsonUrl)
        return gson.fromJson<Health>(json, Health::class.java)
    }

    // Parses errors
    private fun getFromUrl(url: String): JsonObject {
        val json = readJsonUrl(url)
        if (json.has("error")) {
            when (json["error"].asString) {
                "Failed to get player uuid" -> throw InvalidPlayerException()
                "Profile not found!" -> throw ProfileNotFoundException()
                else -> throw SlothpixelApiException(json["error"].asString)
            }
        }
        return json
    }

    @Throws(IOException::class)
    private fun readJsonUrl(url: String): JsonObject {
        val jElement = JsonParser().parse(getPage(url))
        return jElement.asJsonObject
    }

    @Throws(IOException::class)
    private fun getPage(url: String): String {
        val url1 = URL(url)
        val conn: URLConnection = url1.openConnection()
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 5.1; rv:19.0) Gecko/20100101 Firefox/19.0")
        conn.connect()
        val serverResponse = BufferedReader(InputStreamReader(conn.getInputStream()))
        val response = serverResponse.readLine()
        serverResponse.close()
        return response
    }
}