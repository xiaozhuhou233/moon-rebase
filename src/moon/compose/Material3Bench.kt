package moon.compose


object Material3Bench {
    @JvmStatic
    fun main(args: Array<String>) {
        val model = object : MaterialClickGuiModel {
            override fun categoryCount() = 5
            override fun categoryName(index: Int) = listOf("Combat", "Player", "Exploit", "Visual", "Scripts")[index]
            override fun moduleCount(category: Int) = 25
            override fun moduleName(category: Int, module: Int) = "Module $module"
            override fun moduleSuffix(category: Int, module: Int) = "Mode"
            override fun moduleEnabled(category: Int, module: Int) = module % 3 == 0
            override fun toggleModule(category: Int, module: Int) {}
            override fun moduleBindLabel(category: Int, module: Int) = if (module % 4 == 0) "R" else ""
            override fun bindModule(category: Int, module: Int, key: Int) {}
            override fun guiScale() = 1f
            override fun palette() = 0
            override fun setSelectedCategory(category: Int) {}
            override fun requestedModuleSettings() = -1
            override fun settingCount(category: Int, module: Int) = 0
            override fun setting(category: Int, module: Int, setting: Int): MaterialSettingModel = throw UnsupportedOperationException()
            override fun focusTextSetting(setting: MaterialSettingModel) {}
            override fun closeGui() {}
        }
        var total = 0L
        var frames = 0
        try {
            repeat(12) { cycle ->
                Material3GameScene.openClickGui(1920, 1080, model)
                repeat(20) { i ->
                    if (i == 10) Material3GameScene.resize(1280, 720)
                    if (i % 5 == 0) MaterialClickGuiState.railExpanded = !MaterialClickGuiState.railExpanded
                    val r = Material3GameScene.panelRegion()
                    val start = System.nanoTime()
                    val pixels = Material3GameScene.renderRegionBuffer(r[2], r[3])
                    check(pixels.remaining() == r[2] * r[3] * 4)
                    total += System.nanoTime() - start
                    frames++
                    if (cycle == 0 && i == 9 && args.isNotEmpty()) {
                        val image = java.awt.image.BufferedImage(r[2], r[3], java.awt.image.BufferedImage.TYPE_INT_ARGB)
                        for (y in 0 until r[3]) for (x in 0 until r[2]) {
                            val o = (y * r[2] + x) * 4
                            image.setRGB(x, y, ((pixels.get(o + 3).toInt() and 255) shl 24) or ((pixels.get(o + 2).toInt() and 255) shl 16) or ((pixels.get(o + 1).toInt() and 255) shl 8) or (pixels.get(o).toInt() and 255))
                        }
                        javax.imageio.ImageIO.write(image, "png", java.io.File(args[0]))
                    }
                }
                Material3GameScene.close()
                Material3GameScene.close() // Closing an already closed screen must be harmless.
            }
            println("RASTER_LIFECYCLE_PASS cycles=12 frames=$frames avgMs=${total / frames / 1e6}")
        } finally {
            Material3GameScene.close()
        }
    }
}
