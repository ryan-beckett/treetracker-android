package org.greenstand.android.TreeTracker.api

import com.amazonaws.AmazonClientException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.AccessControlList
import com.amazonaws.services.s3.model.GroupGrantee
import com.amazonaws.services.s3.model.Permission
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.PutObjectResult

import org.greenstand.android.TreeTracker.BuildConfig
import org.greenstand.android.TreeTracker.api.DOSpaces.Companion.BUCKET

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import kotlin.String.Companion

/**
 * Created by lei on 12/11/17.
 */

class DOSpaces private constructor() {
    private val s3Client: AmazonS3

    init {
        val basicAWSCredentials = BasicAWSCredentials(BuildConfig.DOS_ACCESS_KEY, BuildConfig.DOS_SECRET_KEY)
        s3Client = AmazonS3Client(basicAWSCredentials)
        s3Client.setEndpoint(ENDPOINT)
    }

    @Throws(AmazonClientException::class)
    fun put(path: String): String {
        val acl = AccessControlList()
        acl.grantPermission(GroupGrantee.AllUsers, Permission.Read)

        val image = File(path)
        val timeStamp = SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(Date())

        val dosKey = timeStamp + '_'.toString() + UUID.randomUUID() + '_'.toString() + image.name
        val poRequest = PutObjectRequest(BUCKET, dosKey, image)
        poRequest.withAccessControlList(acl)
        val poResult = s3Client.putObject(poRequest)
        return String.format("https://%s.nyc3.digitaloceanspaces.com/%s", BUCKET, dosKey)
    }

    companion object {

        val ENDPOINT = "https://nyc3.digitaloceanspaces.com"
        val BUCKET = "treetracker-dev"


        private var sInstance: DOSpaces? = null

        fun instance(): DOSpaces {
            if (sInstance == null) {
                sInstance = DOSpaces()
            }
            return sInstance as DOSpaces
        }
    }

}
