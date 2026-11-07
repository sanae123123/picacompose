package com.picacompose.ui.home

import com.picacompose.ui.components.PicaIcon

/** 底部导航的五个入口，顺序就是栏里的排列顺序。 */
enum class HomeTab(val label: String, val icon: PicaIcon) {
    Home("主页", PicaIcon.Home),
    Category("分类", PicaIcon.Grid),
    Game("游戏", PicaIcon.Gamepad),
    Mine("我的", PicaIcon.Person),
    Settings("设置", PicaIcon.Gear),
}
