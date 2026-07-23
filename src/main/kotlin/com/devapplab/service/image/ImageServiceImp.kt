package com.devapplab.service.image

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.devapplab.model.image.ImageData
import com.devapplab.model.image.ImageMeta
import io.ktor.http.content.*
import io.ktor.server.config.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory
import java.util.*

class ImageServiceImp(config: ApplicationConfig) : ImageService {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val cloudinary: Cloudinary
    
    // Allowed MIME types for security validation
    private val allowedMimeTypes = setOf(
        "image/jpeg", 
        "image/jpg", 
        "image/png", 
        "image/webp",
        "image/gif"
    )
    private val acceptedPartNames = setOf("image")

    init {
        val cloudName = config.property("cloudinary.cloud_name").getString()
        val apiKey = config.property("cloudinary.api_key").getString()
        val apiSecret = config.property("cloudinary.api_secret").getString()

        cloudinary = Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
            )
        )
        logger.info("☁️ Cloudinary initialized for cloud: $cloudName")
    }

    override suspend fun saveImages(multiPartData: MultiPartData, path: String): List<ImageData> {
        val imagesData = mutableListOf<ImageData>()

        // 'path' argument is treated as a 'folder' in Cloudinary to organize images
        val folder = path.trim('/')

        try {
            logger.debug("Starting multipart image parse for folder={}", folder)
            multiPartData.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val partName = part.name?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                    logger.debug("Multipart file part found: name={}", partName.ifBlank { "<empty>" })
                    if (partName !in acceptedPartNames) {
                        logger.info("Discarding multipart file part due to invalid name: name={}", partName.ifBlank { "<empty>" })
                        part.dispose()
                        return@forEachPart
                    }
                    
                    // 1. Basic Security Check: ContentType
                    val contentType = part.contentType?.toString()?.lowercase(Locale.getDefault())
                    if (contentType == null || contentType !in allowedMimeTypes) {
                        logger.info("Discarding multipart file part due to invalid content type: name={}, contentType={}", partName, contentType)
                        part.dispose()
                        return@forEachPart
                    }

                    // Use provider() and readRemaining().readByteArray()
                    val fileBytes = part.provider().readRemaining().readByteArray()
                    
                    // 2. Size Check (Optional, e.g., max 5MB)
                    // if (fileBytes.size > 5 * 1024 * 1024) { ... }

                    // Upload to Cloudinary
                    val uploadResult = withContext(Dispatchers.IO) {
                        cloudinary.uploader().upload(
                            fileBytes,
                            ObjectUtils.asMap(
                                "folder", folder,
                                "resource_type", "image",
                                "type", "authenticated", // 🔒 Make image private/authenticated
                                "access_mode", "authenticated" // Ensure strict access mode
                            )
                        )
                    }

                    // Extract metadata
                    val publicId = uploadResult["public_id"] as String
                    val format = uploadResult["format"] as String
                    val width = (uploadResult["width"] as Int)
                    val height = (uploadResult["height"] as Int)
                    val bytes = (uploadResult["bytes"] as Int).toLong()
                    // secure_url will be returned, but it won't be accessible without signature
                    //val url = uploadResult["secure_url"] as String
                    val etag = uploadResult["etag"] as? String ?: ""

                    val meta = ImageMeta(
                        mime = "image/$format",
                        width = width,
                        height = height,
                        sizeBytes = bytes,
                        checksum = etag 
                    )

                    imagesData.add(ImageData(publicId, meta))
                    logger.info("✅ Image uploaded to Cloudinary (Authenticated): $publicId")

                    part.dispose()
                } else {
                    logger.debug("Discarding non-file multipart part")
                    part.dispose()
                }
            }
        } catch (e: Exception) {
            logger.error("🔥 Error uploading image to Cloudinary", e)
        }
        return imagesData
    }

    override suspend fun deleteImages(path: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // When deleting, we must specify the type if it's not 'upload' (default)
                val params = ObjectUtils.asMap("type", "authenticated")
                val result = cloudinary.uploader().destroy(path, params)
                val resultStr = result["result"] as String
                logger.info("🗑️ Cloudinary delete result for $path: $resultStr")
                resultStr == "ok" || resultStr == "not found" || resultStr == "not_found"
            }
        } catch (e: Exception) {
            logger.error("🔥 Error deleting image from Cloudinary: $path", e)
            false
        }
    }

    override fun getImageUrl(publicId: String): String {
        // Generate a signed URL for authenticated resource
        return cloudinary.url()
            .type("authenticated") // Specify the resource type
            .secure(true)
            .signed(true)
            .generate(publicId)
    }
}
