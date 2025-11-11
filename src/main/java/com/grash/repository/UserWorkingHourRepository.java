package com.grash.repository;

import com.grash.model.UserWorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserWorkingHourRepository extends JpaRepository<UserWorkingHour, Long> {
    List<UserWorkingHour> findByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);
}
