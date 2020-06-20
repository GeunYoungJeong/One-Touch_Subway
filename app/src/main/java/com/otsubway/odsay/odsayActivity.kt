package com.otsubway.odsay

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.odsay.odsayandroidsdk.API
import com.odsay.odsayandroidsdk.ODsayData
import com.odsay.odsayandroidsdk.ODsayService
import com.odsay.odsayandroidsdk.OnResultCallbackListener
import kotlinx.android.synthetic.main.activity_odsay.*
import kotlinx.android.synthetic.main.drive_info.view.*
import kotlinx.android.synthetic.main.station_info.view.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class odsayActivity : AppCompatActivity() {

    private lateinit var odsayService: ODsayService

    lateinit var startStationCode: String
    lateinit var endStationCode: String
    lateinit var startStationName: String
    lateinit var endStationName: String
    lateinit var globalTravelTime: String

    var list = arrayListOf<String>()

    lateinit var pathJsonInfo: JSONObject
    lateinit var timeTableJsonInfo: JSONObject
    lateinit var stations: JSONArray
    lateinit var driveInfo: JSONArray

    val timeTableList = arrayListOf<ArrayList<Int>>()
    val transitStationInfo = arrayListOf<JSONObject>()

    val timeTableJsonInfoList = arrayListOf<JSONObject>()

    var searchOption = "1"
    var firstTravelIndex: Int = 0
    var temp = 0
    var currentTime = 0
    var travelTime = 0
    var dayOfWeekCode: String = ""
    var check = 0
    var driveNumber: Int = 0
    var stationInfoList = arrayListOf<Station>()
    var driveInfoList = arrayListOf<JSONObject>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_odsay)

        val cal = Calendar.getInstance()
        var dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (currentTime < 60) {
            currentTime += 24 * 60
            dayOfWeek--
        }
        travelTime = currentTime

        dayOfWeekCode = when (dayOfWeek) {
            1 -> "SunList"
            in 2..6 -> "OrdList"
            else -> "SatList"
        }

        swiperefresh.setOnRefreshListener {
            swiperefresh.isRefreshing = true
            refreshTime()
            temp = 0
            timeTableList.clear()
            pathViewList.removeAllViews()
            for (i in 0 until timeTableJsonInfoList.size) {
                searchTimeTable(timeTableJsonInfoList[i])
            }
        }

        initODsay()
        initSpinner()

        prevTravelBtn.setOnClickListener {
            if (firstTravelIndex == 0) {
                Toast.makeText(this, "이전 열차가 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
            } else {
                firstTravelIndex--
                travelTime = timeTableList[0][firstTravelIndex]
                pathViewList.removeAllViews()
                attachOnView()
                nextTravelBtn.isClickable = true
            }
        }

        nextTravelBtn.setOnClickListener {
            if (firstTravelIndex == timeTableList[0].size - 1) {
                Toast.makeText(this, "다음 열차가 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
            } else {
                firstTravelIndex++
                travelTime = timeTableList[0][firstTravelIndex]
                pathViewList.removeAllViews()
                attachOnView()
            }
        }

        val i = intent
        list = i.getStringArrayListExtra("stationCodeList") as ArrayList<String>
        startStationCode = list[0]
        endStationCode = list[1]

//        startStationCode = "1316"
//        endStationCode = "720"

        startPathSearch()
    }

    private fun initSpinner() {
        val dayOfWeekAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
        dayOfWeekAdapter.add("평일")
        dayOfWeekAdapter.add("토요일")
        dayOfWeekAdapter.add("공휴일")
        dayOfWeekSpinner.adapter = dayOfWeekAdapter
        when (dayOfWeekCode) {
            "OrdList" -> dayOfWeekSpinner.setSelection(0)
            "SatList" -> dayOfWeekSpinner.setSelection(1)
            else -> dayOfWeekSpinner.setSelection(2)
        }

        dayOfWeekSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //TODO("Not yet implemented")
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                check++
                if (check > 2) {
                    temp = 0
                    refreshTime()
                    timeTableList.clear()
                    pathViewList.removeAllViews()
                    when (position) {
                        0 -> {
                            dayOfWeekCode = "OrdList"
                        }
                        1 -> {
                            dayOfWeekCode = "SatList"
                        }
                        2 -> {
                            dayOfWeekCode = "SunList"
                        }
                    }
                    for (i in 0 until timeTableJsonInfoList.size) {
                        searchTimeTable(timeTableJsonInfoList[i])
                    }
                }
            }
        }

        val travelOptionAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
        travelOptionAdapter.add("최단시간")
        travelOptionAdapter.add("최소환승")
        travelOptionSpinner.adapter = travelOptionAdapter
        travelOptionSpinner.setSelection(0)

        travelOptionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //TODO("Not yet implemented")
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                check++
                if (check > 2) {
                    temp = 0
                    refreshTime()
                    timeTableList.clear()
                    driveInfoList.clear()
                    stationInfoList.clear()
                    transitStationInfo.clear()
                    pathViewList.removeAllViews()
                    when (position) {
                        0 -> {
                            searchOption = "1"
                            startPathSearch()
                        }
                        1 -> {
                            searchOption = "2"
                            startPathSearch()
                        }
                    }
                }
            }
        }
    }

    fun refreshTime() {
        val cal = Calendar.getInstance()
        currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        Log.i("refreshTime ", currentTime.toString())
        if (currentTime < 60) {
            currentTime += 24 * 60
        }
        travelTime = currentTime
    }

    fun initODsay() {
        odsayService =
            ODsayService.init(applicationContext, "RcoymhQZ8l0B/FfV7rRW0nKUPPHZASFWAxC+QNnAs+Q")
        odsayService.setReadTimeout(5000)
        odsayService.setConnectionTimeout(5000)
    }

    private fun startPathSearch() {
        odsayService.requestSubwayPath(
            "1000",
            startStationCode,
            endStationCode,
            searchOption,
            onResultCallbackListener
        )
    }

    private val onResultCallbackListener = object : OnResultCallbackListener {
        // 호출 성공 시 실행
        override fun onSuccess(odsayData: ODsayData, api: API) {
            try {
                when (api) {
                    API.SUBWAY_PATH -> {
                        pathJsonInfo = odsayData.json.getJSONObject("result")
                        Log.i("pathJson ", odsayData.json.getJSONObject("result").toString())
                        startStationName = pathJsonInfo.getString("globalStartName")
                        endStationName = pathJsonInfo.getString("globalEndName")
                        globalTravelTime = pathJsonInfo.getString("globalTravelTime")
                        searchPath(pathJsonInfo)
                    }
                    API.SUBWAY_TIME_TABLE -> {
                        timeTableJsonInfo = odsayData.json.getJSONObject("result")
                        timeTableJsonInfoList.add(timeTableJsonInfo)
                        searchTimeTable(timeTableJsonInfo)
                    }
                    else -> Log.e("API ", api.toString())
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        // 호출 실패 시 실행
        override fun onError(errorCode: Int, errorMessage: String, api: API) {
            //API 예외별로 처리 필요!
            when (errorCode) {
                500 -> Log.d("ErrorMessage", "$api : 서버 내부 오류")
                -8 -> Log.d("ErrorMessage", "$api : 필수 입력값 형식 및 범위 오류")
                -9 -> Log.d("ErrorMessage", "$api : 필수 입력값 누락")
            }
        }
    }

    private fun searchTimeTable(timeTableJsonInfo: JSONObject) {
        val timeList = arrayListOf<Int>()

        val ordList = timeTableJsonInfo.getJSONObject(dayOfWeekCode)

        val wayList = if (ordList.has("up")) {
            ordList.getJSONObject("up")
        } else {
            ordList.getJSONObject("down")
        }

        val time = wayList.getJSONArray("time")
        //Log.i("timeTableJson ", time.toString())

        for (i in 0 until time.length()) {
            val h = time.getJSONObject(i).getInt("Idx")
            val timeListbyMinute =
                time.getJSONObject(i).getString("list").split(" ")
            for (element in timeListbyMinute) {
                timeList.add(h * 60 + element.substring(0, 2).toInt())
            }
        }
        timeTableList.add(timeList)
        temp++
        if (temp == driveNumber) {
            attachOnView()
        }
    }

    private fun searchFastestTime(i: Int): Int {
        val timeList = timeTableList[i]
        for (j in 0 until timeList.size) {
            if (travelTime <= timeList[j]) {
                if (i == 0) firstTravelIndex = j
                travelTime = timeList[j]
                return timeList[j]
            }
        }
        return -1
    }

    private fun searchPath(json: JSONObject) {
        stations = json.getJSONObject("stationSet").getJSONArray("stations")
        driveInfo = json.getJSONObject("driveInfoSet").getJSONArray("driveInfo")
        driveNumber = driveInfo.length()

        runOnUiThread {
            totalFair.text = "${json.getString("fare")}원"
            stationNum.text = "총 ${json.getString("globalStationCount")}개 역"
            transitNum.text = "${driveNumber - 1}회 환승"
        }

        for (i in 0 until driveNumber) {
            driveInfoList.add(
                driveInfo.getJSONObject(i)
            )
        }

        for (i in 0 until driveNumber) {
            stationInfoList.add(
                Station(
                    driveInfo.getJSONObject(i).getString("laneID"),
                    driveInfo.getJSONObject(i).getString("startName")
                )
            )
        }

        if (driveNumber > 1) {
            val exchangeInfo = json.getJSONObject("exChangeInfoSet").getJSONArray("exChangeInfo")

            for (i in 0 until driveNumber - 1) {
                transitStationInfo.add(exchangeInfo.getJSONObject(i))
            }

            stationInfoList.add(
                Station(
                    driveInfo.getJSONObject(driveNumber - 1).getString("laneID"),
                    endStationName
                )
            )

            odsayService.requestSubwayTimeTable(
                stations.getJSONObject(0).getString("startID"),
                driveInfoList[0].getString("wayCode"),
                onResultCallbackListener
            )

            for (i in 1 until driveNumber) {
                odsayService.requestSubwayTimeTable(
                    transitStationInfo[i - 1].getString("exSID"),
                    driveInfoList[i].getString("wayCode"),
                    onResultCallbackListener
                )
            }

        } else {
            stationInfoList.add(
                Station(
                    driveInfo.getJSONObject(0).getString("laneID"),
                    endStationName
                )
            )

            odsayService.requestSubwayTimeTable(
                stations.getJSONObject(0).getString("startID"),
                driveInfoList[0].getString("wayCode"),
                onResultCallbackListener
            )
        }
    }

    private fun attachOnView() {
        swiperefresh.isRefreshing = false
        var startTravelTime = 0
        var partTime = 0
        var spendTime = 0
        var tempStationCount = 0
        var t: Int

        for (i in 0 until stationInfoList.size) {
            val view = layoutInflater.inflate(R.layout.station_info, pathViewList, false)

            val resID = applicationContext.resources.getIdentifier(
                "line${stationInfoList[i].lineCode}",
                "drawable",
                packageName
            )

            view.stationIcon.setImageResource(resID)
            view.stationName.text = stationInfoList[i].stationName

            when (i) {
                0 -> {
                    startTravelTime = searchFastestTime(i)
                    if (startTravelTime == -1) {
                        Toast.makeText(this, "현재 시간에 운행하는 열차가 없습니다", Toast.LENGTH_SHORT).show()
                        scrollView2.visibility = View.GONE
                        noResultView.visibility = View.VISIBLE
                        prevTravelBtn.isClickable = false
                        nextTravelBtn.isClickable = false
                        swiperefresh.isEnabled = false
                        return
                    }
                    view.stationStatus.text = "출발"
                    if (stationInfoList.size == 2) {
                        view.timeView1.visibility = View.GONE
                        view.transText.visibility = View.GONE
                        val param: LinearLayout.LayoutParams =
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
                        view.timeView0.layoutParams = param
                    }
                    view.timeView0.text =
                        String.format("%02d:%02d", travelTime / 60, travelTime % 60)
                }
                stationInfoList.size - 1 -> {
                    if (stationInfoList.size == 2) {
                        view.timeView1.visibility = View.GONE
                        view.transText.visibility = View.GONE
                        val param: LinearLayout.LayoutParams =
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
                        view.timeView0.layoutParams = param
                    }
                    view.stationStatus.text = "도착"
                    travelTime += partTime
                    if (i % 2 == 1) {
                        view.timeView0.text =
                            String.format("%02d:%02d", travelTime / 60, travelTime % 60)
                    } else {
                        view.timeView1.text =
                            String.format("%02d:%02d", travelTime / 60, travelTime % 60)
                    }
                }
                else -> {
                    view.stationStatus.text = "경유"
                    view.transText.visibility = View.VISIBLE
                    if (i % 2 == 1) {
                        travelTime += partTime
                        view.timeView0.text =
                            String.format("%02d:%02d", travelTime / 60, travelTime % 60)
                        travelTime += (transitStationInfo[i - 1].getInt("exWalkTime") / 60) + 1
                        if (searchFastestTime(i) == -1) {
                            Toast.makeText(this, "현재 시간에 운행하는 열차가 없습니다", Toast.LENGTH_SHORT).show()
                            view.timeView1.visibility = View.GONE
                            view.transText.visibility = View.GONE
                            val param: LinearLayout.LayoutParams =
                                LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    3.5f
                                )
                            view.timeView0.layoutParams = param
                            view.timeView0.text = "열차 없음"
                            nextTravelBtn.isClickable = false
                            pathViewList.addView(view)
                            return
                        } else {
                            view.timeView1.text =
                                String.format("%02d:%02d", travelTime / 60, travelTime % 60)
                        }
                    } else {
                        travelTime += partTime
                        view.timeView1.text =
                            String.format("%02d:%02d", travelTime / 60, travelTime % 60)
                        travelTime += (transitStationInfo[i - 1].getInt("exWalkTime") / 60) + 1
                        if (searchFastestTime(i) == -1) {
                            Toast.makeText(this, "현재 시간에 운행하는 열차가 없습니다", Toast.LENGTH_SHORT).show()
                            view.timeView1.visibility = View.GONE
                            view.transText.visibility = View.GONE
                            val param: LinearLayout.LayoutParams =
                                LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    3.5f
                                )
                            view.timeView0.layoutParams = param
                            view.timeView0.text = "열차 없음"
                            nextTravelBtn.isClickable = false
                            pathViewList.addView(view)
                            return
                        } else {
                            view.timeView0.text =
                                String.format("%02d:%02d", travelTime / 60, travelTime % 60)
                        }
                    }
                }
            }
            pathViewList.addView(view)

            if (i < stationInfoList.size - 1) {
                //driveinfo attach
                val driveView = layoutInflater.inflate(R.layout.drive_info, pathViewList, false)

                if (stationInfoList.size == 2) {
                    driveView.tText.visibility = View.GONE
                    driveView.spendView1.visibility = View.GONE
                    val param: LinearLayout.LayoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
                    driveView.spendView0.layoutParams = param
                }

                if (i == stationInfoList.size - 2) {
                    driveView.driveInfoText.text = driveInfoList[i].getString("wayName")
                } else {
                    driveView.driveInfoText.text =
                        "${driveInfoList[i].getString("wayName")}\n빠른 환승${transitStationInfo[i].getInt(
                            "fastDoor"
                        )}"
                }
                tempStationCount += driveInfoList[i].getInt("stationCount")
                t = stations.getJSONObject(tempStationCount - 1)
                    .getInt("travelTime")
                if (i % 2 == 0) {
                    driveView.spendView0.text = "${t - spendTime}분"
                } else {
                    driveView.spendView1.text = "${t - spendTime}분"
                }
                partTime = t - spendTime
                spendTime = t

                val gd = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        colorByLine(stationInfoList[i].lineCode),
                        colorByLine(stationInfoList[i + 1].lineCode)
                    )
                )
                gd.cornerRadius = 30F

                driveView.subwayBar.foreground = gd
                pathViewList.addView(driveView)
            }
            runOnUiThread {
                val totalTravelTime = travelTime - startTravelTime
                if (totalTravelTime >= 60) {
                    totalTime.text =
                        String.format("%d시간 %02d분", totalTravelTime / 60, totalTravelTime % 60)
                } else {
                    totalTime.text = "${totalTravelTime}분"
                }
            }
        }
    }

    fun colorByLine(lineCode: String): Int {
        when (lineCode.toInt()) {
            1 -> return Color.parseColor("#005DAA")
            2 -> return Color.parseColor("#00A44A")
            3 -> return Color.parseColor("#F47D30")
            4 -> return Color.parseColor("#55B2DE")
            5 -> return Color.parseColor("#936FB1")
            6 -> return Color.parseColor("#C77539")
            7 -> return Color.parseColor("#677718")
            8 -> return Color.parseColor("#EA545D")
            9 -> return Color.parseColor("#C6B182")
            21 -> return Color.parseColor("#6E98BB")
            22 -> return Color.parseColor("#ED8B00")
            100 -> return Color.parseColor("#FF9000")
            101 -> return Color.parseColor("#3681B7")
            102 -> return Color.parseColor("#449197")
            104 -> return Color.parseColor("#90C7A7")
            107 -> return Color.parseColor("#4EA346")
            108 -> return Color.parseColor("#178C72")
            109 -> return Color.parseColor("#D31145")
            110 -> return Color.parseColor("#FDA600")
            111 -> return Color.parseColor("#FF9000")
            112 -> return Color.parseColor("#0054A6")
            113 -> return Color.parseColor("#B0CE18")
            114 -> return Color.parseColor("#8FC31E")
            115 -> return Color.parseColor("#AD8605")
        }
        return 0
    }
}