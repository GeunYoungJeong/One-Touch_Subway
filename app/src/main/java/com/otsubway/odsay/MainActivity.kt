package com.otsubway.odsay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.kakao.sdk.newtoneapi.SpeechRecognizeListener
import com.kakao.sdk.newtoneapi.SpeechRecognizerClient
import com.kakao.sdk.newtoneapi.SpeechRecognizerManager
import com.odsay.odsayandroidsdk.API
import com.odsay.odsayandroidsdk.ODsayData
import com.odsay.odsayandroidsdk.ODsayService
import com.odsay.odsayandroidsdk.OnResultCallbackListener
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONException

class MainActivity : AppCompatActivity() {
    val TAG = "KAKAO"
    val RECORD_REQUEST_CODE = 100
    val STORAGE_REQUEST_CODE = 101
    lateinit var odsayService: ODsayService
    val list = arrayListOf<String>()
    var tryNum = 0
    var initTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupPermissions()
        startUsingSpeechSDK()
        initODSay()
        dbButton.setOnClickListener {
            val i = Intent(this, DBActivity::class.java)
            startActivity(i)
        }
        infoButton.setOnClickListener {
            val i = Intent(this, InfoActivity::class.java)
            startActivity(i)
        }
    }

    /**
     * ODSay(지하철 경로 API) 초기화
     */
    private fun initODSay() {
        odsayService =
            ODsayService.init(applicationContext, "RcoymhQZ8l0B/FfV7rRW0nKUPPHZASFWAxC+QNnAs+Q")
        odsayService.setReadTimeout(5000)
        odsayService.setConnectionTimeout(5000)
    }

    /**
     * 권한정보 설정
     */
    private fun setupPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission("AUDIO")
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission("EXTERNAL_STORAGE")
        }
    }

    private fun requestAudioPermission(permission: String) {
        val builder = AlertDialog.Builder(this)
        if (permission == "AUDIO") {
            builder.setMessage("음성 인식을 위해 마이크 권한이 허용되어야 합니다.").setTitle("권한허용")
            builder.setPositiveButton("OK") {
                //2개의 파라티머 구색만 맞춰줌(실제로는 사용하지 않으므로)
                    _, _ ->
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    RECORD_REQUEST_CODE
                )
            }
        } else if (permission == "EXTERNAL_STORAGE") {
            builder.setMessage("음성 인식을 위해 저장공간 권한이 허용되어야 합니다.").setTitle("권한허용")
            builder.setPositiveButton("OK") {
                //2개의 파라티머 구색만 맞춰줌(실제로는 사용하지 않으므로)
                    _, _ ->
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_REQUEST_CODE
                )
            }
        }
        val dlg = builder.create()
        dlg.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "권한이 승인되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
            STORAGE_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "권한이 승인되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * KAKAO STT API 초기화, 음성인식 기능 구현
     */
    private fun startUsingSpeechSDK() {
        //SDK 초기화
        SpeechRecognizerManager.getInstance().initializeLibrary(this)

        //클라이언트 생성
        val builder = SpeechRecognizerClient.Builder()
            .setServiceType(SpeechRecognizerClient.SERVICE_TYPE_LOCAL)
        val client = builder.build()

        //Callback
        client.setSpeechRecognizeListener(object : SpeechRecognizeListener {
            override fun onReady() {
                Log.d(TAG, "모든 하드웨어 및 오디오 서비스가 준비되었습니다.")
                runOnUiThread {
                    tv_result.text = "찾으시는 경로를 말해주세요."
                }
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "사용자가 말을 하기 시작했습니다.")
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "사용자의 말하기가 끝이 났습니다. 데이터를 서버로 전달합니다.")
            }

            override fun onPartialResult(partialResult: String?) {
                //현재 인식된 음성테이터 문자열을 출력해 준다. 여러번 호출됨. 필요에 따라 사용하면 됨.
                runOnUiThread {
                    tv_result.text = partialResult
                }
                Log.d(TAG, "현재까지 인식된 문자열 : $partialResult")
            }

            override fun onResults(results: Bundle?) {
                val texts =
                    results?.getStringArrayList(SpeechRecognizerClient.KEY_RECOGNITION_RESULTS)

                Log.d(TAG, texts?.get(0).toString())

                //정확도가 높은 첫번째 결과값을 텍스트뷰에 출력
                runOnUiThread {
                    tv_result.text = texts?.get(0)
                }
                setStation(texts?.get(0))
            }

            override fun onAudioLevel(audioLevel: Float) {

            }

            override fun onError(errorCode: Int, errorMsg: String) {
                when (errorCode) {
                    SpeechRecognizerClient.ERROR_AUDIO_FAIL -> {
                        Log.e(TAG, errorMsg)
                        runOnUiThread { tv_result.text = "음성입력이 불가능하거나\n마이크 접근이 허용되지 않았습니다." }
                    }
                    SpeechRecognizerClient.ERROR_NETWORK_FAIL -> {
                        Log.e(TAG, errorMsg)
                        runOnUiThread { tv_result.text = "네트워크 오류가 발생했습니다.\n인터넷 상태를 확인해주세요." }
                    }
                    SpeechRecognizerClient.ERROR_NO_RESULT -> {
                        Log.e(TAG, errorMsg)
                        runOnUiThread { tv_result.text = "인식된 결과 목록이 없습니다." }
                    }
                    SpeechRecognizerClient.ERROR_CLIENT -> {
                        Log.e(TAG, errorMsg)
                        runOnUiThread { tv_result.text = "\" ~ 에서(부터) ~ \"\n으로 말해주세요." }
                    }
                    SpeechRecognizerClient.ERROR_SERVER_ALLOWED_REQUESTS_EXCESS -> {
                        Log.e(TAG, errorMsg)
                        runOnUiThread { tv_result.text = "요청 허용 횟수를 초과했습니다." }
                    }
                    else -> {
                        Log.e(TAG, errorMsg)
                        runOnUiThread { tv_result.text = "시스템 오류입니다.\n${errorMsg}" }
                    }
                }
            }
            override fun onFinished() {
            }
        })

        bt_start.setOnClickListener {
            client.startRecording(true)
        }
    }

    /**
     * 음성인식 된 텍스트로 역 정보 추출, 역 이름에서 역 코드 가져오는 API 호출
     */
    fun setStation(inputText: String?) {
        if (inputText != null) {
            var resultText = inputText.replace(" ", "")

            //일부 역(전대.에버랜드역 등등)이 .을 사용하고 있어서 대체.
            if (resultText.contains("전대에버랜드")) {
                resultText = resultText.replace("전대에버랜드", "전대.에버랜드")
            }
            if (resultText.contains("운동장송담대")) {
                resultText = resultText.replace("운동장송담대", "운동장.송담대")
            }
            if (resultText.contains("시청용인대")) {
                resultText = resultText.replace("시청용인대", "시청.용인대")
            }
            if (resultText.contains("419민주묘지")) {
                resultText = resultText.replace("419민주묘지", "4.19민주묘지")
            }

            lateinit var station: ArrayList<String>
            if (resultText.contains("에서")) {
                station = resultText.split("에서") as ArrayList<String>
            } else if (resultText.contains("부터")) {
                station = resultText.split("부터") as ArrayList<String>
            }

            Log.i("Start", station[0])
            Log.i("End", station[1])

            /**
             * 데이터베이스에서 항목(사용자 위치) 검색
             * 일치하면 역이름으로 대체
             */
            try {
                val readDB =
                    this.openOrCreateDatabase("stationByLocation.db", Context.MODE_PRIVATE, null)
                for (i in 0..1) {
                    val strsql =
                        "select * from locations where userLocation = \'" + station[i] + "\'"
                    val c: Cursor = readDB.rawQuery(strsql, null)
                    if (c.count != 0) {
                        c.moveToFirst()
                        station[i] = c.getString(c.getColumnIndex("station"))
                        Log.i("station ", station[i])
                    }
                }
            } catch (e: SQLiteException) {
                Log.i("No SQL ", e.message)
            }

            for(i in 0..1) {
                if(station[i][station[i].length - 1] == '역') {
                    station[i] = station[i].substring(0, station[i].length - 1)
                }
                if(station[i] == "서울") {
                    station[i] = "서울역"
                }
            }

            if (station[0] == station[1]) {
                tv_result.text = "출발지와 도착지가 같습니다.\n다시 시도해주세요."
            } else {
                changeToStationCode(station[0], station[1])
            }
        }
    }

    private fun changeToStationCode(startStation: String, endStation: String) {
        odsayService.requestSearchStation(
            startStation,
            "1000",
            "2",
            "1",
            "0",
            "",
            onResultCallbackListener
        )

        odsayService.requestSearchStation(
            endStation,
            "1000",
            "2",
            "1",
            "0",
            "",
            onResultCallbackListener
        )
    }

    /**
     * requestSearchStation의 CallbackListener
     * JSON 정보를 반환해 역 이름이 유효한지 검사
     * 유효하다면 JSON 데이터 startODsay 함수에 넘겨준다.
     */
    private val onResultCallbackListener = object : OnResultCallbackListener {
        // 호출 성공 시 실행
        override fun onSuccess(odsayData: ODsayData, api: API) {
            try {
                val json = odsayData.json
                if (json.has("result")) {
                    val result = json.getJSONObject("result")
                    if (result.getInt("totalCount") > 0) {
                        val station = result.getJSONArray("station")
                        val startCode = station.getJSONObject(0).getString("stationID")
                        startODsay(startCode)
                    } else {
                        tryNum++
                        if (tryNum == 2) {
                            tv_result.text = "올바르지 않은 역 이름입니다.\n다시 시도해주세요."
                            list.clear()
                            tryNum = 0
                        }
                    }
                } else {
                    tryNum++
                    if (tryNum == 2) {
                        tv_result.text = "올바르지 않은 역 이름입니다.\n다시 시도해주세요."
                        list.clear()
                        tryNum = 0
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace();
            }
        }

        // 호출 실패 시 실행
        override fun onError(errorCode: Int, errorMessage: String, api: API) {
            when (errorCode) {
                500 -> Log.e("ODSAYError", "$api : 서버 내부 오류")
                -8 -> Log.e("ODSAYError", "$api : 필수 입력값 형식 및 범위 오류")
                -9 -> Log.e("ODSAYError", "$api : 필수 입력값 누락")
            }
        }
    }

    /**
     * 출발, 도착역 모두 유효한지 검사
     * 둘중 하나라도 유효하지 않으면, 에러메시지 출력, 다시 시도
     * 둘다 유효하다면 itent로 코드 리스트 odsayActivity 시작
     */
    fun startODsay(code: String) {
        tryNum++
        list.add(code)
        Log.i("list add ", "$code ${list.size} $tryNum")
        if (tryNum == 2 && list.size == 2) {
            val i = Intent(this, odsayActivity::class.java)
            i.putStringArrayListExtra("stationCodeList", list)
            startActivity(i)
            list.clear()
            tryNum = 0
        } else if (tryNum == 2 && list.size != 2) {
            tv_result.text = "올바르지 않은 역 이름입니다.\n다시 시도해주세요."
            list.clear()
            tryNum = 0
        }
    }

    /**
     * 뒤로가기 키 1.5초 안에 두번 누르면 프로그램 종료
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if ((System.currentTimeMillis() - initTime) > 1500) {
                    Toast.makeText(this, "종료하려면 한번 더 누르세요.", Toast.LENGTH_SHORT).show()
                    initTime = System.currentTimeMillis()
                } else {
                    finish()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 어플리케이션 종료되면 API 종료
     */
    override fun onDestroy() {
        super.onDestroy()
        SpeechRecognizerManager.getInstance().finalizeLibrary()
    }
}
