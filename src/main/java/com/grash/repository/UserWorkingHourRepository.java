package com.grash.repository;

import com.grash.model.UserWorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserWorkingHourRepository extends JpaRepository<UserWorkingHour, Long> {
    List<UserWorkingHour> findByUser_Id(Long userId);

    List<UserWorkingHour> findByUser_IdIn(List<Long> userIds);

    void deleteByUser_Id(Long userId);
}
