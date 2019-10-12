package com.morioka.thirdproject.adapter

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.ui.CreateQuestion
import com.morioka.thirdproject.ui.MemberStatus
import com.morioka.thirdproject.ui.OthersQuestions
import com.morioka.thirdproject.ui.OwnQuestions

class TabsPagerAdapter(fm: FragmentManager, private val user: User, private val sessionId: String?) : FragmentStatePagerAdapter(fm) {
    private val pageTitle = arrayOf("自分の質問", "他人の質問", "新規作成", "会員情報")

    override fun getItem(position: Int): Fragment {
        when (position) {
            0 -> {
                return OwnQuestions.newInstance(sessionId, user, position)
            }
            1 -> {
                return OthersQuestions.newInstance(sessionId, user, position)
            }
            2 -> {
                return CreateQuestion.newInstance(sessionId, user, position)
            }
            3 -> {
                return MemberStatus.newInstance(sessionId, user, position)
            }
            else -> {
                return OwnQuestions.newInstance(sessionId, user, 0)
            }
        }
    }
    // タブの名前
    override fun getPageTitle(position: Int): CharSequence? {
        return pageTitle[position]
    }
    // タブの個数
    override fun getCount(): Int {
        return pageTitle.size
    }

//    fun getCurrentPosition(): Int {
//        return getItemPosition()
//    }

//    // タブの変更
//    fun getTabView(tabLayout: TabLayout, position: Int): View {
//        // tab_item.xml を複数
//        val view = LayoutInflater.from(this.context).inflate(R.layout.tab_item, tabLayout, false)
//        // タイトル
//        val tab = view.findViewById<TextView>(R.id.teb_item_text)
//        tab.text = pageTitle[position]
//        // アイコン
//        val image = view.findViewById<ImageView>(R.id.teb_item_image)
//        image.setImageResource(R.mipmap.ic_launcher)
//        return view
//    }
}