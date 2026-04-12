package top.aidanrao.buaa_classhopper.data.model.dto

data class AnnouncementDto(
    val id: String,
    val title: String,
    val content: String?,
    val posterUserId: String?,
    val cover: String?,
    val createTime: String,
    val updateTime: String?,
    val deleted: String?,
    val targetUserType: String?,
    val targetAppKeys: String?,
    val posterUsername: String?,
    val posterAvatar: String?
)

data class AnnouncementDetailDto(
    val id: String,
    val title: String,
    val content: String?,
    val posterUserId: String?,
    val cover: String?,
    val createTime: String,
    val updateTime: String?,
    val deleted: Boolean,
    val targetUserType: String?,
    val posterUsername: String?,
    val posterAvatar: String?
)
