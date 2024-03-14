package com.example.demo.doll

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest


@SpringBootTest
class DollApplicationTests {
    @Autowired
    lateinit var controller: StockController
    private val log = LoggerFactory.getLogger(this.javaClass)!!

    @BeforeEach
    fun setUp(): Unit = runBlocking {
        //매 테스트 전 각 인형의 재고를 0으로 초기화
        launch(Dispatchers.IO) {
            val doll1 = controller.inquire(1L)
            controller.unStore(1L, doll1.stock);
        }
        launch(Dispatchers.IO) {
            val doll2 = controller.inquire(2L)
            controller.unStore(2L, doll2.stock);
        }
    }

    @DisplayName("재고 조회, 입고, 출고 정상 동작 여부 테스트")
    @Test
    fun inquireAndStoreAndUnStoreTest() = runBlocking {
        //재고 조회 테스트
        val inquireJob = async(Dispatchers.IO) {
            return@async controller.inquire(1L)
        }
        var doll = inquireJob.await();
        assertEquals(0L, doll.stock)

        //입고 조회 테스트
        val storeJob = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }
        doll = storeJob.await();
        assertEquals(10000L, doll.stock)

        //출고 조회 테스트
        val unStoreJob = async(Dispatchers.IO) {
            return@async controller.unStore(1L, 5000L)
        }
        doll = unStoreJob.await();
        assertEquals(5000L, doll.stock)
    }

    @DisplayName("같은 인형에게 충분한 시간 차를 두고 입고시는 두 입고 요청 다 성공")
    @Test
    fun storeSuccessIfNotInARowForOneDoll() = runBlocking {
        //첫 번째 입고
        val storeJob1 = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        //충분한 시간 차 후 (DB 부분을 보면 입고에 300L 밀리세컨드가 소요되므로 1000L이면 충분히 나중이다)
        delay(1000L)

        //두 번째 입고
        val storeJob2 = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        joinAll(storeJob1, storeJob2);
        val doll = controller.inquire(1L);
        assertEquals(20000L, doll.stock)
    }

    @DisplayName("같은 인형에게 연속적으로 입고시는 첫 번째 요청 반영, 두 번째 요청 에러 반환")
    @Test
    fun storeFailIfInARowForOneDoll(): Unit = runBlocking {
        //첫 번째 입고
        val storeJob1 = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        //거의 바로 연속으로 (DB 부분을 보면 입고에 300L 밀리세컨드가 소요되므로 10L이면 동시에 요청이 온것으로 볼 수 있다.)
        delay(10L)

        //두 번째 입고
        val storeJob2 = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        joinAll(storeJob1, storeJob2)

        //두 번째 입고가 예외를 던지며 실패하는 것 확인
        try {
            storeJob2.await()
            fail("No Exception");
        } catch (e : Exception) {
            assertTrue(e is CancellationException)
            assertEquals("입고가 불가능합니다.", e.message)
        }

        //첫 번째 입고만 반영된 것 확인
        val doll = controller.inquire(1L)
        assertEquals(10000L, doll.stock)
    }

    @DisplayName("같은 인형에게 연속적으로 출고시는 둘 다 정상 반영")
    @Test
    fun unStoreSuccessIfInARowForOneDoll(): Unit = runBlocking {
        //테스트 셋팅 용 입고 처음 재고를 10000으로 맞춘다.
        val settingJob = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        settingJob.join();

        //첫 번째 출고
        val unStoreJob1 = async(Dispatchers.IO) {
            return@async controller.unStore(1L, 2500L)
        }

        //거의 바로 연속으로 (DB 부분을 보면 출고에 300L 밀리세컨드가 소요되므로 10L이면 동시에 요청이 온것으로 볼 수 있다.)
        delay(10L)

        //두 번째 출고
        val unStoreJob2 = async(Dispatchers.IO) {
            return@async controller.unStore(1L, 2500L)
        }

        joinAll(settingJob, unStoreJob1, unStoreJob2)

        //첫 번째 출고 요청과 두 번째 출고 요청이 모두 반영 된 것 확인
        val doll = controller.inquire(1L)
        assertEquals(5000L, doll.stock)
    }

    @DisplayName("서로 다른 인형에게 각각 두번 연속으로 입고시 각 인형별로 첫 번째 요청 반영, 두 번째 요청 에러 반환")
    @Test
    fun storeFailIfInARowForManyDoll(): Unit = runBlocking {
        //인형 1에게 첫 번째 입고
        val storeJob1ForDoll1 = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        //인형 2에게 첫 번째 입고
        val storeJob1ForDoll2 = async(Dispatchers.IO) {
            return@async controller.store(2L, 10000L)
        }

        //거의 바로 연속으로 (DB 부분을 보면 입고에 300L 밀리세컨드가 소요되므로 10L이면 동시에 요청이 온것으로 볼 수 있다.)
        delay(10L)

        //인형 1에게 두 번째 입고
        val storeJob2ForDoll1 = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        //인형 2에게 두 번째 입고
        val storeJob2ForDoll2 = async(Dispatchers.IO) {
            return@async controller.store(2L, 10000L)
        }

        joinAll(storeJob1ForDoll1, storeJob1ForDoll2, storeJob2ForDoll1, storeJob2ForDoll2)

        //첫 번째 인형에게 두 번째 입고가 예외를 던지며 실패하는 것 확인
        try {
            storeJob2ForDoll1.await()
            fail("No Exception");
        } catch (e : Exception) {
            assertTrue(e is CancellationException)
            assertEquals("입고가 불가능합니다.", e.message)
        }

        //두 번째 인형에게 두 번째 입고가 예외를 던지며 실패하는 것 확인
        try {
            storeJob2ForDoll2.await()
            fail("No Exception");
        } catch (e : Exception) {
            assertTrue(e is CancellationException)
            assertEquals("입고가 불가능합니다.", e.message)
        }

        //첫 번째 인형에게 첫 번째 입고만 반영된 것 확인
        val doll1 = controller.inquire(1L)
        assertEquals(10000L, doll1.stock)

        //두 번째 인형에게 첫 번째 입고만 반영된 것 확인
        val doll2 = controller.inquire(2L)
        assertEquals(10000L, doll2.stock)
    }

    @DisplayName("서로 다른 인형에게 각각 두번 연속으로 출고시 각 인형별로 둘 다 정상 반영")
    @Test
    fun unStoreSuccessIfInARowForManyDoll(): Unit = runBlocking {
        //테스트 셋팅 용 입고 처음 재고를 10000으로 맞춘다.
        val settingJobForDoll1 = async(Dispatchers.IO) {
            return@async controller.store(1L, 10000L)
        }

        //테스트 셋팅 용 입고 처음 재고를 10000으로 맞춘다.
        val settingJobForDoll2 = async(Dispatchers.IO) {
            return@async controller.store(2L, 10000L)
        }

        joinAll(settingJobForDoll1, settingJobForDoll2)

        //인형 1에게 첫 번째 출고
        val unStoreJob1ForDoll1 = async(Dispatchers.IO) {
            return@async controller.unStore(1L, 2500L)
        }

        //인형 2에게 첫 번째 출고
        val unStoreJob1ForDoll2 = async(Dispatchers.IO) {
            return@async controller.unStore(2L, 2500L)
        }

        //거의 바로 연속으로 (DB 부분을 보면 출고에 300L 밀리세컨드가 소요되므로 10L이면 동시에 요청이 온것으로 볼 수 있다.)
        delay(10L)

        //인형 1에게 두 번째 출고
        val unStoreJob2ForDoll1 = async(Dispatchers.IO) {
            return@async controller.unStore(1L, 2500L)
        }

        //인형 2에게 두 번째 출고
        val unStoreJob2ForDoll2 = async(Dispatchers.IO) {
            return@async controller.unStore(2L, 2500L)
        }

        joinAll(unStoreJob1ForDoll1, unStoreJob1ForDoll2, unStoreJob2ForDoll1, unStoreJob2ForDoll2)

        //첫 번째 인형에게 첫 번째 입고만 반영된 것 확인
        val doll1 = controller.inquire(1L)
        assertEquals(5000L, doll1.stock)

        //두 번째 인형에게 첫 번째 입고만 반영된 것 확인
        val doll2 = controller.inquire(2L)
        assertEquals(5000L, doll2.stock)
    }

    @DisplayName("서로 다른 인형에게 각각 연속으로 입고하고 출고 할 시 (혹은 출고하고 입고 할 시) 둘 다 정상 반영")
    @Test
    fun storeAndUnStoreSuccessIfInARowForManyDoll(): Unit = runBlocking {
        //인형 1에게 첫 번째 입고 (인형 1은 입고 먼저 이후 출고)
        val storeJobForDoll1 = async(Dispatchers.IO) {
            return@async controller.store(1L, 3000L)
        }

        //인형 2에게 첫 번째 출고 (인형 2는 출고 먼저 이후 입고)
        val unStoreJobForDoll2 = async(Dispatchers.IO) {
            return@async controller.unStore(2L, 2000L)
        }

        //거의 바로 연속으로 (DB 부분을 보면 입출고에 300L 밀리세컨드가 소요되므로 10L이면 동시에 요청이 온것으로 볼 수 있다.)
        delay(10L)

        //인형 1에게 두 번째 출고
        val unStoreJobForDoll1 = async(Dispatchers.IO) {
            return@async controller.unStore(1L, 2000L)
        }

        //인형 2에게 두 번째 출고
        val storeJobForDoll2 = async(Dispatchers.IO) {
            return@async controller.store(2L, 3000L)
        }

        joinAll(storeJobForDoll1, unStoreJobForDoll2, unStoreJobForDoll1, storeJobForDoll2)

        //첫 번째 인형에게 첫 번째 입고만 반영된 것 확인
        val doll1 = controller.inquire(1L)
        assertEquals(1000L, doll1.stock)

        //두 번째 인형에게 첫 번째 입고만 반영된 것 확인
        val doll2 = controller.inquire(2L)
        assertEquals(1000L, doll2.stock)
    }
}
