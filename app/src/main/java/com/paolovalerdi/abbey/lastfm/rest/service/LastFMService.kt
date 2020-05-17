package com.paolovalerdi.abbey.lastfm.rest.service

import com.paolovalerdi.abbey.lastfm.rest.model.LastFmAlbum
import com.paolovalerdi.abbey.lastfm.rest.model.LastFmArtist
import com.paolovalerdi.abbey.lastfm.rest.model.LastFmSimilarArtist
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface LastFMService {

    @GET("$BASE_QUERY_PARAMETERS&method=album.getinfo")
    fun getAlbumInfo(@Query("album") albumName: String, @Query("artist") artistName: String, @Query("lang") language: String?): Call<LastFmAlbum>

    @GET("$BASE_QUERY_PARAMETERS&method=artist.getinfo")
    fun getArtistInfo(@Query("artist") artistName: String, @Query("lang") language: String?, @Header("Cache-Control") cacheControl: String?): Call<LastFmArtist>

    @GET("$BASE_QUERY_PARAMETERS&method=artist.getSimilar&limit=5")
    fun getSimilarArtistTo(@Query("artist") artistName: String): Call<LastFmSimilarArtist>

    companion object {

        const val API_KEY = "bd9c6ea4d55ec9ed3af7d276e5ece304"
        const val BASE_QUERY_PARAMETERS = "?format=json&autocorrect=1&api_key=$API_KEY"
    }

}