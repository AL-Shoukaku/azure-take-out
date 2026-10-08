package com.sky.service.impl;

import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressBookServiceImplTest extends ServiceTestSupport {
    @InjectMocks private AddressBookServiceImpl service;
    @Mock private AddressBookMapper mapper;

    @Test void newAddressUsesAuthenticatedUserAndIsNotDefault() {
        AddressBook address = AddressBook.builder().userId(999L).isDefault(1).phone("13800000000").build();
        service.save(address);
        verify(mapper).insert(address);
        assertThat(address.getUserId()).isEqualTo(USER_ID);
        assertThat(address.getIsDefault()).isZero();
        assertThat(address.getPhone()).isEqualTo("13800000000");
    }
    @Test void listReturnsMatchingAddresses() {
        AddressBook filter = AddressBook.builder().userId(USER_ID).isDefault(1).build();
        AddressBook address = AddressBook.builder().id(7L).userId(USER_ID).build();
        when(mapper.list(filter)).thenReturn(Collections.singletonList(address));
        assertThat(service.list(filter)).containsExactly(address);
    }
    @Test void detailReturnsOwnAddress() {
        AddressBook address = AddressBook.builder().id(7L).userId(USER_ID).build();
        when(mapper.getById(7L)).thenReturn(address);
        assertThat(service.getById(7L)).isSameAs(address);
    }
    @Test void missingAddressReturnsNull() { assertThat(service.getById(7L)).isNull(); }
    @Test void updatePassesAddressFieldsToMapper() {
        AddressBook address = AddressBook.builder().id(7L).userId(USER_ID).detail("新地址").build();
        service.update(address); verify(mapper).update(address);
    }
    @Test void setDefaultClearsCurrentUsersDefaultsBeforeSettingTarget() {
        AddressBook address = AddressBook.builder().id(7L).userId(USER_ID).build();
        List<Integer> valuesAtInvocation = new ArrayList<>();
        doAnswer(i -> { valuesAtInvocation.add(((AddressBook)i.getArgument(0)).getIsDefault()); return null; })
                .when(mapper).updateIsDefaultByUserId(any());
        doAnswer(i -> { valuesAtInvocation.add(((AddressBook)i.getArgument(0)).getIsDefault()); return null; })
                .when(mapper).update(any());
        service.setDefault(address);
        InOrder sequence = inOrder(mapper);
        sequence.verify(mapper).updateIsDefaultByUserId(address); sequence.verify(mapper).update(address);
        assertThat(valuesAtInvocation).containsExactly(0,1);
        assertThat(address.getUserId()).isEqualTo(USER_ID);
    }
    @Test void deleteOwnAddress() { service.deleteById(7L); verify(mapper).deleteById(7L); }
}
