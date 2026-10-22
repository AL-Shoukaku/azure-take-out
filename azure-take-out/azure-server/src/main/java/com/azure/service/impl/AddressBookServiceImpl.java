package com.azure.service.impl;

import com.azure.context.BaseContext;
import com.azure.constant.MessageConstant;
import com.azure.entity.AddressBook;
import com.azure.exception.AddressBookBusinessException;
import com.azure.exception.UserNotLoginException;
import com.azure.mapper.AddressBookMapper;
import com.azure.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        Long userId = getCurrentUserId();
        if (addressBook == null) {
            addressBook = new AddressBook();
        }
        addressBook.setUserId(userId);
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(getCurrentUserId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        Long userId = getCurrentUserId();
        AddressBook addressBook = addressBookMapper.getByIdAndUserId(id, userId);
        if (addressBook == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
        return addressBook;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    public void update(AddressBook addressBook) {
        getById(addressBook.getId());
        addressBook.setUserId(getCurrentUserId());
        if (addressBookMapper.update(addressBook) == 0) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        AddressBook ownedAddress = getById(addressBook.getId());
        Long userId = ownedAddress.getUserId();
        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBookMapper.updateIsDefaultByUserId(AddressBook.builder().userId(userId).isDefault(0).build());

        //2、将当前用户的目标地址改为默认地址
        AddressBook target = AddressBook.builder().id(ownedAddress.getId()).userId(userId).isDefault(1).build();
        if (addressBookMapper.update(target) == 0) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
    }

    /**
     * 根据id删除地址
     *
     * @param id
     */
    public void deleteById(Long id) {
        getById(id);
        if (addressBookMapper.deleteById(id, getCurrentUserId()) == 0) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
    }

    private Long getCurrentUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        return userId;
    }

}
