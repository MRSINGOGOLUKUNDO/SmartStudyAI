package com.zut.smartstudy

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.zut.smartstudy.notifications.NotificationHelper

class SmartStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        NotificationHelper.createChannel(this)
    }
}
