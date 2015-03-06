/*
    This file is part of RouteConverter.

    RouteConverter is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    RouteConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RouteConverter; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Copyright (C) 2007 Christian Pesch. All Rights Reserved.
*/
package slash.navigation.csv;

import org.junit.Test;
import slash.navigation.base.NavigationFormatParser;
import slash.navigation.base.ParserResult;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static slash.navigation.base.NavigationTestCase.TEST_PATH;

public class ExcelFormatIT {
    private NavigationFormatParser parser = new NavigationFormatParser();

    @Test
    public void testReadXls() throws IOException {
        File source = new File(TEST_PATH + "from.xls");
        ParserResult result = parser.read(source);
        assertNotNull(result);
        assertEquals(Excel97Format.class, result.getFormat().getClass());
        assertEquals(2036, result.getTheRoute().getPositionCount());
    }

    @Test
    public void testReadXlsx() throws IOException {
        File source = new File(TEST_PATH + "from.xlsx");
        ParserResult result = parser.read(source);
        assertNotNull(result);
        assertEquals(Excel2008Format.class, result.getFormat().getClass());
        assertEquals(2036, result.getTheRoute().getPositionCount());
    }
}
