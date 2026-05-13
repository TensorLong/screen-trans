package com.galaxy.airviewdictionary.data.local.secure

import android.content.Context
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * 무료 번역 시간제한 정보
 *
 * trialStartTime: 시작시간 - 앱 설치시
 * trialTimeMinute: 제한시간
 */
object TrialLimitInfo {

    private fun getTrialStartTime(context: Context): Long {
        val trialStartTime: SecureString = SecureStore.get(context, SecureStoreKey.TRIAL_START_TIME)?.let {
            it
        } ?: run {
            setTrialStartTime(context, System.currentTimeMillis())
        }
        return trialStartTime.get().toLong()
    }

    private fun setTrialStartTime(context: Context, trialStartTimeMillis: Long): SecureString {
        return SecureString(trialStartTimeMillis.toString()).also {
            SecureStore.set(context, SecureStoreKey.TRIAL_START_TIME, it.get())
        }
    }

    fun addTrialTime(context: Context, trialTimeMin: Int) {
        val trialStartTimeLong: Long = getTrialStartTime(context)
        val trialTimeMillis = trialTimeMin * 60 * 1000L
        setTrialStartTime(context, trialStartTimeLong + trialTimeMillis)
    }

    private fun getTrialTimeLimitMinute(context: Context): Int {
        val trialTimeMinute: SecureString = SecureStore.get(context, SecureStoreKey.TRIAL_TIME_LIMIT_MINUTE) ?: run {
            SecureString(0.toString()).also {
                SecureStore.set(context, SecureStoreKey.TRIAL_TIME_LIMIT_MINUTE, it.get())
            }
        }
        return trialTimeMinute.get().toInt()
    }

    fun setTrialTimeLimitMinute(context: Context, trialTimeLimitMinute: Int) {
        SecureStore.set(context, SecureStoreKey.TRIAL_TIME_LIMIT_MINUTE, trialTimeLimitMinute.toString())
    }

    fun isTrialAvailable(context: Context): Boolean {
        return trialRemainMinutes(context) > 0
    }

    /**
     * trial remain minute
     */
    fun trialRemainMinutes(context: Context): Int {
        // 시간제한이 설정되지 않은 경우 무제한 무료
        val trialTimeMinute = getTrialTimeLimitMinute(context)
        if (trialTimeMinute == 0) {
            return 1
        }

        // 그 외 설정된 시간제한 에서 사용시작 시간부터 현재시간 까지를 뺀 시간
        val trialStartTimeLong: Long = getTrialStartTime(context)
        val differenceInMillis = System.currentTimeMillis() - trialStartTimeLong
        val differenceInMinutes = differenceInMillis / (1000 * 60)
        Timber.tag("TrialLimitInfo").d("trialTimeMinute $trialTimeMinute")
        Timber.tag("TrialLimitInfo").d("trialStartTimeLong $trialStartTimeLong")
        Timber.tag("TrialLimitInfo").d("differenceInMinutes $differenceInMinutes")
        Timber.tag("TrialLimitInfo").d("trialRemainMinutes ${trialTimeMinute - differenceInMinutes.toInt()}")

        if(trialTimeMinute - differenceInMinutes.toInt() < 0) {
            setTrialStartTime(context, System.currentTimeMillis() - trialTimeMinute * 60 * 1000L)
        }
        return maxOf(trialTimeMinute - differenceInMinutes.toInt(), 0).apply {
            Timber.tag("TrialLimitInfo").d("return trialTimeMinute $this")
        }
    }

    fun trialElapsedHours(context: Context): Int {
        val currentTime = System.currentTimeMillis()
        val trialStartTime: Long = (SecureStore.get(context, SecureStoreKey.TRIAL_START_TIME)
            ?: SecureString(System.currentTimeMillis().toString()))
            .get().toLong()
        val differenceInMillis = currentTime - trialStartTime
        return (differenceInMillis / (1000 * 60 * 60)).toInt()
    }

    fun trialElapsedDays(context: Context): Int {
        val currentTime = System.currentTimeMillis()
        val trialStartTime: Long = (SecureStore.get(context, SecureStoreKey.TRIAL_START_TIME)
            ?: SecureString(System.currentTimeMillis().toString()))
            .get().toLong()
        val differenceInMillis = currentTime - trialStartTime
        return (differenceInMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    fun setFixedAreaViewCampaignPeriodMinute(context: Context, fixedAreaViewCampaignPeriodMinute: Int) {
        SecureStore.set(context, SecureStoreKey.FIXED_AREA_VIEW_CAMPAIGN_PERIOD_MINUTE, fixedAreaViewCampaignPeriodMinute.toString())
    }

    fun getFixedAreaViewCampaignPeriodMinute(context: Context): Int {
        val trialTimeMinute: SecureString = SecureStore.get(context, SecureStoreKey.FIXED_AREA_VIEW_CAMPAIGN_PERIOD_MINUTE)?.let {
            it
        } ?: run {
            SecureString(10.toString()).also {
                SecureStore.set(context, SecureStoreKey.FIXED_AREA_VIEW_CAMPAIGN_PERIOD_MINUTE, it.get())
            }
        }
        return trialTimeMinute.get().toInt()
    }

    fun toString(context: Context): String {
        return "TrialLimitInfo {\n" +
                "\ttrialStartTime: ${formatTime(getTrialStartTime(context))},\n" +
                "\ttrialTimeMinute: ${getTrialTimeLimitMinute(context)},\n" +
                "\ttrialRemainMinutes(): ${trialRemainMinutes(context)},\n" +
                "\tisTrialAvailable(): ${isTrialAvailable(context)},\n" +
                "\tfixedAreaViewCampaignPeriodMinute: ${getFixedAreaViewCampaignPeriodMinute(context)},\n" +
                "}"
    }

    private fun formatTime(time: Long?): String {
        if (time == null) {
            return "null"
        } else {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date(time))
        }
    }
}


