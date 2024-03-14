package com.example.demo.doll

import com.example.demo.database.Database
import kotlinx.coroutines.sync.Mutex
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

@RestController
@RequestMapping("/doll")
class StockController(val db: Database) {
    private val lockMap: MutableMap<String, Mutex> = ConcurrentHashMap()

    @GetMapping("{id}/inquire")
    fun inquire(@PathVariable id: Long): Doll {
        return db.getStock(id)
    }

    @PostMapping("{id}/store")
    suspend fun store(@PathVariable id: Long, @RequestBody amount: Long): Doll {
        val storeLock: Mutex = lockMap.computeIfAbsent("store-$id") { k -> Mutex() }
        val unStoreLock: Mutex = lockMap.computeIfAbsent("unStore-$id") { k -> Mutex() }

        check(!(!unStoreLock.isLocked && storeLock.isLocked)) {
            throw CancellationException("입고가 불가능합니다.")
        }

        try {
            storeLock.lock();

            val doll = db.getStock(id)
            val targetStock = doll.stock + amount
            db.setStock(id, targetStock)
            return db.getStock(id)
        } finally {
            storeLock.unlock()
        }
    }

    @PostMapping("{id}/unStore")
    suspend fun unStore(@PathVariable id: Long, @RequestBody amount: Long): Doll {
        val storeLock: Mutex = lockMap.computeIfAbsent("store-$id") { k -> Mutex() }
        val unStoreLock: Mutex = lockMap.computeIfAbsent("unStore-$id") { k -> Mutex() }

        try {
            storeLock.lock()
            unStoreLock.lock()
            val doll = db.getStock(id)
            val targetStock = doll.stock - amount
            db.setStock(id, targetStock)
            return db.getStock(id)
        } finally {
            unStoreLock.unlock()
            storeLock.unlock()
        }
    }
}