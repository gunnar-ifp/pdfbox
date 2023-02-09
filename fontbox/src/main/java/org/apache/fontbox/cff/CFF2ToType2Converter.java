/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fontbox.cff;

import java.io.IOException;

import org.apache.fontbox.cff.CharStringCommand.CFF2Command;
import org.apache.fontbox.cff.CharStringCommand.CommandConsumer;
import org.apache.fontbox.cff.CharStringCommand.Type2Command;

/**
 * Converts a CFF2 charstring into a Type2 charstring.<br>
 * 
 * @author Gunnar Brand
 * @since 09.02.2023
 */
class CFF2ToType2Converter implements CommandConsumer<CFF2Command>
{
    private final CommandConsumer<Type2Command> consumer;
    private final CharStringOperandStack stack = new CharStringOperandStack();

    
    public CFF2ToType2Converter(CommandConsumer<Type2Command> consumer)
    {
        this.consumer = consumer;
    }


    @Override
    public void apply(CFF2Command command, CharStringOperandStack args) throws IOException
    {
//        System.out.println("cff2c: " + command + ", " + args.size());

        switch (command) {
            case VSINDEX:
                break;
                
            case BLEND:
                int n = args.popInt();
                while ( args.size()>n ) args.pop();
                break;

            default:
                Type2Command t2c = CharStringCommand.getEnum(Type2Command.class, command.name());
                if ( t2c==null ) {
                    // TODO: Log here
                } else {
                    consumer.apply(t2c, args);
                }
        }
    }
    
    
    @Override
    public void end() throws IOException
    {
        consumer.apply(Type2Command.ENDCHAR, stack);
        consumer.end();
    }
    
}
