package com.galaxy.airviewdictionary.ui.screen.permissions


/**
 * 권한 요청 상태를 나타냄
 */
enum class PermissionStatus {
    Prepared, // 권한 요청 준비중
    Ready, // 권한 요청 준비됨
    Requested, // 권한 요청
    Granted, // 권한 허가됨
    Denied, // 권한 거부됨
    Canceled, // 취소됨
}