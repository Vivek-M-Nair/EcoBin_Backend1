package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled tasks that run automatically:
 * 1. Daily: Assign workers to zones 2 days before collection.
 * 2. Daily: Generate OTPs for zones with collection today.
 * 3. Daily: Auto-generate schedules for zones past 15-day cooldown.
 * 4. Monthly (6th): Clear "add_amount_to_be_paid" in collection_Expenses.
 */
@Component
public class ScheduledTasks {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Autowired private SchedulingService schedulingService;
    @Autowired private LastTimeCollectionRepository lastTimeCollectionRepository;
    @Autowired private CollectionScheduleRepository scheduleRepository;
    @Autowired private CollectionExpenseRepository expenseRepository;

    /**
     * Runs daily at 6 AM: assign workers for collections 2 days out.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void dailyWorkerAssignment() {
        System.out.println("[SCHEDULER] Running daily worker assignment...");
        schedulingService.assignWorkersForUpcomingCollections();
        System.out.println("[SCHEDULER] Worker assignment completed.");
    }

    /**
     * Runs daily at 5 AM: generate OTPs for zones with collection scheduled today.
     */
    @Scheduled(cron = "0 0 5 * * *")
    public void dailyOtpGeneration() {
        String today = LocalDate.now().format(DATE_FMT);
        System.out.println("[SCHEDULER] Generating OTPs for collection date: " + today);

        List<CollectionSchedule> todaySchedules = scheduleRepository.findByScheduledDate(today);
        for (CollectionSchedule schedule : todaySchedules) {
            schedulingService.generateOtpsForZone(schedule.getZoneId(), today);
        }
        System.out.println("[SCHEDULER] OTP generation completed for " + todaySchedules.size() + " zones.");
    }

    /**
     * Runs daily at 7 AM: check all zones and auto-schedule if past 15-day cooldown
     * and no active schedule exists.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void dailyAutoSchedule() {
        System.out.println("[SCHEDULER] Running auto-schedule check...");

        List<LastTimeCollection> allZones = lastTimeCollectionRepository.findAll();
        int scheduled = 0;

        for (LastTimeCollection ltc : allZones) {
            String zoneId = ltc.getZoneId();
            LocalDate lastCollected = LocalDate.parse(ltc.getLastCollectedDate(), DATE_FMT);
            long daysSince = ChronoUnit.DAYS.between(lastCollected, LocalDate.now());

            if (daysSince >= 15) {
                // Check if already scheduled
                List<CollectionSchedule> existing = scheduleRepository.findByZoneId(zoneId);
                boolean alreadyScheduled = existing.stream()
                        .anyMatch(s -> "scheduled".equals(s.getStatus()));

                if (!alreadyScheduled) {
                    schedulingService.generateScheduleForZone(zoneId);
                    scheduled++;
                }
            }
        }

        System.out.println("[SCHEDULER] Auto-scheduled " + scheduled + " zones.");
    }

    /**
     * Runs on the 6th of every month at midnight:
     * Clear "add_amount_to_be_paid" field in collection_Expenses.
     */
    @Scheduled(cron = "0 0 0 6 * *")
    public void monthlyExpenseClear() {
        System.out.println("[SCHEDULER] Clearing monthly expense reimbursements...");

        List<CollectionExpense> allExpenses = expenseRepository.findAll();
        for (CollectionExpense expense : allExpenses) {
            if (expense.getAddAmountToBePaid() > 0) {
                expense.setAddAmountToBePaid(0);
                expenseRepository.save(expense);
            }
        }

        System.out.println("[SCHEDULER] Monthly expense clear completed.");
    }
}
