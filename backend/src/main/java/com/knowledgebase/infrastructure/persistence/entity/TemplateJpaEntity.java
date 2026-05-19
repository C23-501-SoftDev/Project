package com.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "templates")
@Getter
@Setter
public class TemplateJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    private String description;
    
    @Column(nullable = false)
    private String content;
    
    @Column(nullable = false)
    private String role;
    
    @Column(nullable = false)
    private boolean isSystem;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
