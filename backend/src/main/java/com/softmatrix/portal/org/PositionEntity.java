package com.softmatrix.portal.org;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "job_position")
public class PositionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 50, unique = true)
    private String name;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
