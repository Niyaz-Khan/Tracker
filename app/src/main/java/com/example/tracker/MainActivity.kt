package com.example.tracker

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

//Делаешь беговой трекер с картой

//1) Главный экран, отображается сколько шагов человек прошел за день,
// какое расстояние прошел за день, список тренировок,
// кнопка для перехода на второй экран(Начать тренировку)
//2) Второй экран, карта + таймер сколько длится тренировка, шаги, расстояние,
// карта отцентрирована по юзеру и строится маршрут на карте, можно приостановить,
// завершить тренировку по нажатию на кнопку, так же при нажатии на нее переход идет
// на 3 экран с результатом
//3) Третий экран, результат тренировки с пройденным путем на карте
//Для данных о тренировках пользователя есть гугловый HealthConnect, для карт много чего есть,
// я работал с MapBox, у них крутая дока и хорошие примеры

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        supportFragmentManager.beginTransaction().replace(R.id.main, StepTrackerFragment()).commit()
    }
}