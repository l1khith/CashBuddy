package com.cashbuddy.di

import com.cashbuddy.ocr.MlKitOcrEngine
import com.cashbuddy.ocr.OcrEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    single<OcrEngine> { MlKitOcrEngine(androidContext()) }
}
