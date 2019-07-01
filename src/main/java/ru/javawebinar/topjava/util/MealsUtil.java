package ru.javawebinar.topjava.util;

import ru.javawebinar.topjava.model.Meal;
import ru.javawebinar.topjava.model.MealTo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.LocalTime.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;
import static ru.javawebinar.topjava.util.Util.isBetween;

public class MealsUtil {

    public static final List<Meal> MEALS = Arrays.asList(
            new Meal(LocalDateTime.of(2015, Month.MAY, 30, 10, 0), "Завтрак", 500),
            new Meal(LocalDateTime.of(2015, Month.MAY, 30, 13, 0), "Обед", 1000),
            new Meal(LocalDateTime.of(2015, Month.MAY, 30, 20, 0), "Ужин", 500),
            new Meal(LocalDateTime.of(2015, Month.MAY, 31, 10, 0), "Завтрак", 1000),
            new Meal(LocalDateTime.of(2015, Month.MAY, 31, 13, 0), "Обед", 500),
            new Meal(LocalDateTime.of(2015, Month.MAY, 31, 20, 0), "Ужин", 510)
    );

    public static final int DEFAULT_CALORIES_PER_DAY = 2000;

    public static void main(String[] args) {
        final List<MealTo> filteredWithExcess = getFilteredWithExcess(MEALS, of(7, 0), of(12, 0), 2000);
        final List<MealTo> filteredWithExcessByCycle = getFilteredWithExcessByCycle(MEALS, of(7, 0), of(12, 0), 2000);
        final List<MealTo> filteredWithExcessInOnePass1 = getFilteredWithExcessInOnePass1(MEALS, of(7, 0), of(12, 0), 2000);
        final List<MealTo> filteredWithExcessInOnePass2 = getFilteredWithExcessInOnePass2(MEALS, of(7, 0), of(12, 0), 2000);

        assert filteredWithExcess.equals(filteredWithExcessByCycle);
        assert filteredWithExcessByCycle.equals(filteredWithExcessInOnePass1);
        assert filteredWithExcessInOnePass1.equals(filteredWithExcessInOnePass2);
        assert filteredWithExcessInOnePass2.equals(filteredWithExcess);
        assert filteredWithExcess.equals(filteredWithExcessInOnePass1);
        assert filteredWithExcessByCycle.equals(filteredWithExcessInOnePass2);

        filteredWithExcess
                .forEach(System.out::println);
        filteredWithExcessByCycle
                .forEach(System.out::println);
        filteredWithExcessInOnePass1
                .forEach(System.out::println);
        filteredWithExcessInOnePass2
                .forEach(System.out::println);
    }

    public static List<MealTo> getWithExcess(Collection<Meal> meals, int caloriesPerDay) {
        return getFilteredWithExcess(meals, caloriesPerDay, meal -> true);
    }

    public static List<MealTo> getFilteredWithExcess(Collection<Meal> meals, int caloriesPerDay, LocalTime startTime, LocalTime endTime) {
        return getFilteredWithExcess(meals, caloriesPerDay, meal -> isBetween(meal.getTime(), startTime, endTime));
    }

    private static List<MealTo> getFilteredWithExcess(Collection<Meal> meals, int caloriesPerDay, Predicate<Meal> filter) {
        Map<LocalDate, Integer> caloriesSumByDate = meals.stream()
                .collect(
                        Collectors.groupingBy(Meal::getDate, Collectors.summingInt(Meal::getCalories))
//                      Collectors.toMap(Meal::getDate, Meal::getCalories, Integer::sum)
                );

        return meals.stream()
                .filter(filter)
                .map(meal -> createWithExcess(meal, caloriesSumByDate.get(meal.getDate()) > caloriesPerDay))
                .collect(toList());
    }

    public static List<MealTo> getFilteredWithExcess(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Map<LocalDate, Integer> caloriesSumByDate = meals
                .stream()
                .collect(
                        groupingBy(Meal::getDate, summingInt(Meal::getCalories))
//                      toMap(Meal::getDate, Meal::getCalories, Integer::sum)
                );

        return meals.stream()
                .filter(meal -> isBetween(meal.getTime(), startTime, endTime))
                .map(meal -> new MealTo(meal.getId(), meal.getDateTime(), meal.getDescription(), meal.getCalories(), caloriesSumByDate.get(meal.getDate()) > caloriesPerDay))
                .collect(toList());
    }

    public static List<MealTo> getFilteredWithExcessByCycle(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {

        final Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
        meals.forEach(meal -> caloriesSumByDate.merge(meal.getDate(), meal.getCalories(), Integer::sum));

        final List<MealTo> mealsWithExcess = new ArrayList<>();
        meals.forEach(meal -> {
            if (isBetween(meal.getTime(), startTime, endTime)) {
                mealsWithExcess.add(createWithExcess(meal, caloriesSumByDate.get(meal.getDate()) > caloriesPerDay));
            }
        });
        return mealsWithExcess;
    }

    public static List<MealTo> getFilteredWithExcessInOnePass1(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Collection<List<Meal>> list = meals.stream()
                .collect(groupingBy(Meal::getDate)).values();

        return list.stream().flatMap(dayMeals -> {
            boolean excess = dayMeals.stream().mapToInt(Meal::getCalories).sum() > caloriesPerDay;
            return dayMeals.stream().filter(meal ->
                    isBetween(meal.getTime(), startTime, endTime))
                    .map(meal -> createWithExcess(meal, excess));
        }).collect(toList());
    }

    public static List<MealTo> getFilteredWithExcessInOnePass2(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        final class Aggregate {
            private final List<Meal> dailyMeals = new ArrayList<>();
            private int dailySumOfCalories;

            private void accumulate(Meal meal) {
                dailySumOfCalories += meal.getCalories();
                if (isBetween(meal.getDateTime().toLocalTime(), startTime, endTime)) {
                    dailyMeals.add(meal);
                }
            }

            // never invoked if the upstream is sequential
            private Aggregate combine(Aggregate that) {
                this.dailySumOfCalories += that.dailySumOfCalories;
                this.dailyMeals.addAll(that.dailyMeals);
                return this;
            }

            private Stream<MealTo> finisher() {
                final boolean excess = dailySumOfCalories > caloriesPerDay;
                return dailyMeals.stream().map(meal -> createWithExcess(meal, excess));
            }
        }

        Collection<Stream<MealTo>> values = meals.stream()
                .collect(groupingBy(Meal::getDate,
                        Collector.of(Aggregate::new, Aggregate::accumulate, Aggregate::combine, Aggregate::finisher))
                ).values();

        return values.stream().flatMap(identity()).collect(toList());
    }

    private static MealTo createWithExcess(Meal meal, boolean excess) {
        return new MealTo(meal.getId(), meal.getDateTime(), meal.getDescription(), meal.getCalories(), excess);
    }
}