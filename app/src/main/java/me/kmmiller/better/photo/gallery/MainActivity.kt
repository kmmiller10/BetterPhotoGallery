package me.kmmiller.better.photo.gallery

import android.os.Bundle
import androidx.lifecycle.ViewModel
import io.realm.Realm
import me.kmmiller.baseui.KmmBaseActivity
import me.kmmiller.baseui.navigation.BottomNavItemModel

class MainActivity : KmmBaseActivity() {
    var realm: Realm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        realm = Realm.getDefaultInstance()
        if(savedInstanceState == null) {
            pushFragment(PhotoGridFragment(), replace = true, addToBackStack = false, tag = PhotoGridFragment::class.java.name)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm?.close()
        realm = null
    }

    // No bottom nav
    override var hasBottomNav: Boolean = false
    override fun defaultNavItem(): Int = 0
    override fun getHighlightColor(): Int = 0
    override fun getNavItems(): ArrayList<BottomNavItemModel> = arrayListOf()
    override fun navItemSelected(itemId: Int) {}
}