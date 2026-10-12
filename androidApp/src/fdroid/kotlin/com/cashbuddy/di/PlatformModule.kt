package com.cashbuddy.di

import com.cashbuddy.ocr.OcrEngine
import com.cashbuddy.ocr.TesseractOcrEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    single<OcrEngine> { TesseractOcrEngine(androidContext()) }
}
