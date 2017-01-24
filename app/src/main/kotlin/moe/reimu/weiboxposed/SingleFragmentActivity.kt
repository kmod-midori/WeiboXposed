package moe.reimu.weiboxposed

import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity

abstract class SingleFragmentActivity : AppCompatActivity() {

    protected abstract fun createFragment(): Fragment

    protected val layoutResId: Int
        @LayoutRes
        get() = R.layout.activity_fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResId)

        val fragmentManager = supportFragmentManager

        val fragment = fragmentManager.findFragmentById(R.id.fragment_container) ?: createFragment()

        fragmentManager.beginTransaction()
                .add(R.id.fragment_container, fragment)
                .commit()
    }

}