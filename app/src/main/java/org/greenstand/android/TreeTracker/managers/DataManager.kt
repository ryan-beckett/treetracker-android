package org.greenstand.android.TreeTracker.managers

import org.greenstand.android.TreeTracker.api.Api
import org.greenstand.android.TreeTracker.api.models.responses.UserTree

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

/**
 * This is entry point for latest API, "trees/details/user" and "trees/create"
 */

abstract class DataManager<T> {

    private val mApi: Api

    init {
        mApi = Api.instance()
    }

    abstract fun onDataLoaded(data: T?)

    abstract fun onRequestFailed(message: String?)

    fun loadUserTrees() {
        val trees = mApi.api?.treesForUser
        trees?.enqueue(object : Callback<List<UserTree>> {
            override fun onResponse(call: Call<List<UserTree>>, response: Response<List<UserTree>>) {
                if (response.isSuccessful) {
                    onDataLoaded(response.body() as T?)
                }
            }

            override fun onFailure(call: Call<List<UserTree>>, t: Throwable) {
                onRequestFailed(t.message)
                Timber.tag(TAG).e(t.message)
            }
        })
    }

    companion object {

        private val TAG = "DataManager"
    }

}
