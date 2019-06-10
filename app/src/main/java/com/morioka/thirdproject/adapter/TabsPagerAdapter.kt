package com.morioka.thirdproject.adapter

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.ui.CreateQuestion
import com.morioka.thirdproject.ui.MemberStatus
import com.morioka.thirdproject.ui.OthersQuestions
import com.morioka.thirdproject.ui.OwnQuestions

class TabsPagerAdapter(fm: FragmentManager, private val context: Context,private val user: User, private val sessionId: String) : FragmentStatePagerAdapter(fm) {
    private val pageTitle = arrayOf("自分の質問", "他人の質問", "新規作成", "会員情報")

    override fun getItem(position: Int): Fragment {
        when (position) {
            0 -> {
                return OwnQuestions.newInstance(sessionId, user)
            }
            1 -> {
                return OthersQuestions.newInstance(sessionId, user)
            }
            2 -> {
                return CreateQuestion.newInstance(sessionId, user)
            }
            3 -> {
                return MemberStatus.newInstance(sessionId, user)
            }
            else -> {
                return OwnQuestions.newInstance(sessionId, user)
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

//    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
//        super.destroyItem(container, position, `object`)
//
//        if (position <= count) {
//            val manager = (`object` as Fragment).fragmentManager
//            val trans = manager!!.beginTransaction()
//            trans.remove(`object`)
//            trans.commit()
//        }
//    }

//    fun destroyAllItem(pager: ViewPager) {
//        for (i in 0 until count - 1) {
//            try {
//                val obj = this.instantiateItem(pager, i)
//                if (obj != null)
//                    destroyItem(pager, i, obj)
//            } catch (e: Exception) {
//            }
//
//        }
//    }

//    fun setArgument(frag: Fragment){
//        frag.arguments = Bundle().apply {
//            putString("USER_ID", userId)
//            putString("SESSION_ID", sessionId)
//            putInt("STATUS", status)
//        }
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