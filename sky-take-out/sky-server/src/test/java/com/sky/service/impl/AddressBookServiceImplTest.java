package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.AddressBookBusinessException;
import com.sky.mapper.AddressBookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressBookServiceImplTest {

    @InjectMocks
    private AddressBookServiceImpl addressBookService;
    @Mock private AddressBookMapper addressBookMapper;

    @BeforeEach
    void setCurrentUser() {
        BaseContext.setCurrentId(1L);
    }

    @AfterEach
    void clearCurrentUser() {
        BaseContext.removeCurrentId();
    }

    @Test
    void userCannotReadOrChangeAnotherUsersAddress() {
        AddressBook address = AddressBook.builder().id(5L).userId(2L).build();
        when(addressBookMapper.getById(5L)).thenReturn(address);

        assertNull(addressBookService.getById(5L));
        assertThrows(AddressBookBusinessException.class, () -> addressBookService.update(address));
        assertThrows(AddressBookBusinessException.class, () -> addressBookService.setDefault(address));
        assertThrows(AddressBookBusinessException.class, () -> addressBookService.deleteById(5L));

        verify(addressBookMapper, never()).update(any(AddressBook.class));
        verify(addressBookMapper, never()).deleteById(5L);
        verify(addressBookMapper, never()).updateIsDefaultByUserId(any(AddressBook.class));
    }
}
