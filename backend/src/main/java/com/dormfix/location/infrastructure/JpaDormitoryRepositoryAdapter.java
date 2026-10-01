package com.dormfix.location.infrastructure;

import com.dormfix.location.application.DormitoryRepository;
import com.dormfix.location.domain.Dormitory;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaDormitoryRepositoryAdapter implements DormitoryRepository {
    private final SpringDataDormitoryRepository delegate;

    JpaDormitoryRepositoryAdapter(SpringDataDormitoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Dormitory save(Dormitory dormitory) {
        return delegate.save(dormitory);
    }

    @Override
    public Optional<Dormitory> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Dormitory> findAllByActiveTrueOrderByNameAsc() {
        return delegate.findAllByActiveTrueOrderByNameAsc();
    }
}
