package org.chaosadept.summaryzatron2000.model;


import jakarta.persistence.*;
import lombok.Data;


@Data
@Entity
@Table(name = "td_user")
public class User {
    @Id
    private Long id;
    private String username;
}
