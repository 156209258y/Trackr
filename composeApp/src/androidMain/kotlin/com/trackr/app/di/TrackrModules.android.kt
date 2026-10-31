package com.trackr.app.di

import com.trackr.feature.route.routeAndroidModule
import org.koin.core.module.Module

/** Android：高德 SDK 通道、Key 注入、地图 SDK 初始化（:feature:route androidMain） */
internal actual val platformFeatureModules: List<Module> = listOf(routeAndroidModule)
