/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Benoit Delbosc
 *     Antoine Taillefer
 */
package org.nuxeo.functionaltests.pages.admincenter.usermanagement;

import org.nuxeo.functionaltests.Required;
import org.nuxeo.functionaltests.forms.Select2WidgetElement;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Edit user details (New one in the admin center)
 *
 * @since 5.4.2
 */
public class UserEditFormPage extends UsersGroupsBasePage {

    @Required
    @FindBy(id = "viewUserView:editUser:nxl_user_1:nxw_firstname_1")
    WebElement firstnameInput;

    @Required
    @FindBy(id = "viewUserView:editUser:nxl_user_1:nxw_lastname_1")
    WebElement lastnameInput;

    @Required
    @FindBy(id = "viewUserView:editUser:nxl_user_1:nxw_company_1")
    WebElement companyInput;

    @Required
    @FindBy(id = "viewUserView:editUser:nxl_user_1:nxw_email_1")
    WebElement emailInput;

    @Required
    @FindBy(xpath = "//input[@value=\"Save\"]")
    WebElement saveButton;

    public UserEditFormPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Edit a user, only update non null value.
     *
     * @param firstname
     * @param lastname
     * @param company
     * @param email
     * @param password
     * @param group
     * @throws NoSuchElementException
     */
    public UserViewTabSubPage editUser(String firstname, String lastname,
            String company, String email, String group)
            throws NoSuchElementException {
        updateInput(firstnameInput, firstname);
        updateInput(lastnameInput, lastname);
        updateInput(companyInput, company);
        updateInput(emailInput, email);
        if (group != null) {
            Select2WidgetElement groups = new Select2WidgetElement(
                    driver,
                    driver.findElement(By.xpath("//*[@id='s2id_viewUserView:editUser:nxl_user_1:nxw_groups_1_select2']")),
                    true);
            groups.selectValue(group);
        }
        saveButton.click();
        return asPage(UserViewTabSubPage.class);
    }

    private void updateInput(WebElement elem, String value) {
        if (value != null) {
            elem.clear();
            elem.sendKeys(value);
        }
    }

}
