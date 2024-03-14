package com.example.demo.database

import com.example.demo.doll.Doll
import org.springframework.stereotype.Component
import java.lang.Math.random
@Component
class Database {
    private val db: HashMap<Long, Doll> = HashMap()

    fun setStock(id: Long, amount: Long): Doll {
        Thread.sleep(random().toLong() * 300L + 100)
        val doll = Doll(stock = amount, updateMilli = System.currentTimeMillis(), updateNano = System.nanoTime())
        db[id] = doll
        return doll
    }

    fun getStock(id: Long): Doll {
        Thread.sleep(random().toLong() * 100L + 100)
        return db[id] ?: Doll(stock = 0, updateMilli = System.currentTimeMillis(), updateNano = System.nanoTime())
    }
}