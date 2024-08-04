package dev.legendsayantan.extendroid.data

import dev.legendsayantan.extendroid.services.ExtendService

/**
 * @author legendsayantan
 */
data class ActiveSession(val id:Int, val pkg:String, val windowInfo:String, val mode:ExtendService.Companion.WindowMode,val port:Int = -1)