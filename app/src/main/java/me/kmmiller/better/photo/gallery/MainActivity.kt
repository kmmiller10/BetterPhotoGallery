package me.kmmiller.better.photo.gallery

import android.os.Bundle
import android.view.MenuItem
import io.realm.Realm
import me.kmmiller.baseui.KmmBaseActivity
import me.kmmiller.baseui.navigation.BottomNavItemModel
import me.kmmiller.better.photo.gallery.extensions.withNullable

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

    override fun onBackPressed() {
        val frag = supportFragmentManager.fragments.firstOrNull {
            it is BackPressFragment
        }

        if((frag as? BackPressFragment)?.onBackPress() == false) {
            // Not handled by frag
            super.onBackPressed()
        }
    }

    fun updateToolbarBackBtn(showBack: Boolean) {
        withNullable(supportActionBar) {
            setDisplayHomeAsUpEnabled(showBack)
            setDisplayShowHomeEnabled(showBack)
        }
        invalidateOptionsMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // No bottom nav
    override var hasBottomNav: Boolean = false
    override fun defaultNavItem(): Int = 0
    override fun getHighlightColor(): Int = 0
    override fun getNavItems(): ArrayList<BottomNavItemModel> = arrayListOf()
    override fun navItemSelected(itemId: Int) {}
}