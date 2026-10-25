# Difference between how CPU and FPGA execute programs

Consider the GCD example:

<pre>
uint32_t gcd(uint32_t a, uint32_t b) {
  while (a != b) {
    if (a > b) {
      a = a-b;
    } else {
      b = b-a;
    }
  }
  return a | b;
}
</pre>

G++ generates the following code from it:

<pre>
   0x0000000000001180 <+0>:  cmp    %esi,%edi
   0x0000000000001182 <+2>:  je     0x119e <_Z3gcdjj+30>
   0x0000000000001184 <+4>:  nopl   0x0(%rax)                          // alignment no-op to align jump target address to 8 bytes
   0x0000000000001188 <+8>:  cmp    %edi,%esi
   0x000000000000118a <+10>: jae    0x1198 <_Z3gcdjj+24>
   0x000000000000118c <+12>: sub    %esi,%edi
   0x000000000000118e <+14>: cmp    %edi,%esi
   0x0000000000001190 <+16>: jne    0x1188 <_Z3gcdjj+8>
   0x0000000000001192 <+18>: mov    %esi,%eax
   0x0000000000001194 <+20>: ret    
   0x0000000000001195 <+21>: nopl   (%rax)                             // alignment no-op to align jump target address to 8 bytes
   0x0000000000001198 <+24>: sub    %edi,%esi
   0x000000000000119a <+26>: cmp    %esi,%edi
   0x000000000000119c <+28>: jne    0x1188 <_Z3gcdjj+8>
   0x000000000000119e <+30>: mov    %edi,%eax
   0x00000000000011a0 <+32>: ret    
</pre>

The state here is <code>ESI</code>, <code>EDI</code> and <code>RIP</code>. What the CPU does is fetch the next instruction, execute it, and repeat.

The same algorithm in Flows, as part of a small calculator that also multiplies (GCD is on lines 26–40):

<style>
.s0 { display: inline; color: #c0c0c0 }
.s1 { display: inline; color: #80c080 }
.s2 { display: inline; color: #c08080 }
.s3 { display: inline; color: #808080 }
</style>
<pre>  1  <div class="s0"></div><div class="s1">struct</div><div class="s0"> MultiplyRequest </div><div class="s2">{</div><div class="s0"> A</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"> B</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"> </div><div class="s2">}</div><div class="s0"></div>
  2  <div class="s0"></div><div class="s1">struct</div><div class="s0"> GCDRequest </div><div class="s2">{</div><div class="s0"> A</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"> B</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"> </div><div class="s2">}</div><div class="s0"></div>
  3  <div class="s0"></div><div class="s1">oneof</div><div class="s0"> CalculatorRequest </div><div class="s2">{</div><div class="s0"> MultiplyRequest</div><div class="s2">,</div><div class="s0"> GCDRequest </div><div class="s2">}</div><div class="s0"></div>
  4  <div class="s0"></div><div class="s1">struct</div><div class="s0"> CalculatorResult </div><div class="s2">{</div><div class="s0"> Value</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"> </div><div class="s2">}</div><div class="s0"></div>
  5  <div class="s0"></div><div class="s1">oneof</div><div class="s0"> BlankOrCalculatorResult </div><div class="s2">{</div><div class="s0"> Blank</div><div class="s2">,</div><div class="s0"> CalculatorResult </div><div class="s2">}</div><div class="s0"></div>
  6  <div class="s0"></div>
  7  <div class="s0"></div><div class="s1">transform</div><div class="s0"> CalculatorTransform </div><div class="s2">(</div><div class="s0">Request</div><div class="s2">:</div><div class="s0"> CalculatorRequest</div><div class="s2">,</div><div class="s0"> Response</div><div class="s2">:</div><div class="s0"> BlankOrCalculatorResult</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
  8  <div class="s0">    </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0"></div><div class="s1">true</div><div class="s0"></div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
  9  <div class="s0">        </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">RequestReader </div><div class="s1">is</div><div class="s0"> CalculatorRequest</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 10  <div class="s0">            RequestPop </div><div class="s2">=</div><div class="s0"> 1</div><div class="s2">;</div><div class="s0"></div>
 11  <div class="s0">            </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">RequestReader</div><div class="s2">.</div><div class="s0">CalculatorRequest </div><div class="s1">is</div><div class="s0"> MultiplyRequest</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 12  <div class="s0">                Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"> Product </div><div class="s2">=</div><div class="s0"> 0</div><div class="s2">;</div><div class="s0"></div>
 13  <div class="s0">                Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"> MultiplyA </div><div class="s2">=</div><div class="s0"> RequestReader</div><div class="s2">.</div><div class="s0">CalculatorRequest</div><div class="s2">.</div><div class="s0">MultiplyRequest</div><div class="s2">.</div><div class="s0">A</div><div class="s2">;</div><div class="s0"></div>
 14  <div class="s0">                Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"> MultiplyB </div><div class="s2">=</div><div class="s0"> RequestReader</div><div class="s2">.</div><div class="s0">CalculatorRequest</div><div class="s2">.</div><div class="s0">MultiplyRequest</div><div class="s2">.</div><div class="s0">B</div><div class="s2">;</div><div class="s0"></div>
 15  <div class="s0">                </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 16  <div class="s0">                </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0">MultiplyB </div><div class="s2">!=</div><div class="s0"> 0</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 17  <div class="s0">                    </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">Slice</div><div class="s2">(</div><div class="s0">whole</div><div class="s2">:</div><div class="s0"> MultiplyB</div><div class="s2">,</div><div class="s0"> offsetInBits</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">,</div><div class="s0"> countInBits</div><div class="s2">:</div><div class="s0"> 1</div><div class="s2">)</div><div class="s0"> </div><div class="s2">==</div><div class="s0"> 1</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 18  <div class="s0">                        Product </div><div class="s2">=</div><div class="s0"> Product </div><div class="s2">+</div><div class="s0"> MultiplyA</div><div class="s2">;</div><div class="s0"></div>
 19  <div class="s0">                    </div><div class="s2">}</div><div class="s0"></div>
 20  <div class="s0">                    MultiplyA </div><div class="s2">=</div><div class="s0"> ShiftLeft</div><div class="s2">(</div><div class="s0">what</div><div class="s2">:</div><div class="s0"> MultiplyA</div><div class="s2">,</div><div class="s0"> howManyBits</div><div class="s2">:</div><div class="s0"> 1</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 21  <div class="s0">                    MultiplyB </div><div class="s2">=</div><div class="s0"> ShiftRight</div><div class="s2">(</div><div class="s0">what</div><div class="s2">:</div><div class="s0"> MultiplyB</div><div class="s2">,</div><div class="s0"> howManyBits</div><div class="s2">:</div><div class="s0"> 1</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 22  <div class="s0">                    </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 23  <div class="s0">                </div><div class="s2">}</div><div class="s0"></div>
 24  <div class="s0">                Response </div><div class="s2">=</div><div class="s0"> CalculatorResult</div><div class="s2">{</div><div class="s0">Value</div><div class="s2">:</div><div class="s0"> Product</div><div class="s2">}</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 25  <div class="s0">            </div><div class="s2">}</div><div class="s0"> </div><div class="s1">else</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 26  <div class="s0">                </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">RequestReader</div><div class="s2">.</div><div class="s0">CalculatorRequest </div><div class="s1">is</div><div class="s0"> GCDRequest</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 27  <div class="s0">                    Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"> GCDA </div><div class="s2">=</div><div class="s0"> RequestReader</div><div class="s2">.</div><div class="s0">CalculatorRequest</div><div class="s2">.</div><div class="s0">GCDRequest</div><div class="s2">.</div><div class="s0">A</div><div class="s2">;</div><div class="s0"></div>
 28  <div class="s0">                    Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"> GCDB </div><div class="s2">=</div><div class="s0"> RequestReader</div><div class="s2">.</div><div class="s0">CalculatorRequest</div><div class="s2">.</div><div class="s0">GCDRequest</div><div class="s2">.</div><div class="s0">B</div><div class="s2">;</div><div class="s0"></div>
 29  <div class="s0">                    </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">GCDA </div><div class="s2">!=</div><div class="s0"> 0 </div><div class="s2">&amp;&amp;</div><div class="s0"> GCDB </div><div class="s2">!=</div><div class="s0"> 0</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 30  <div class="s0">                        </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 31  <div class="s0">                        </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0">GCDA </div><div class="s2">!=</div><div class="s0"> GCDB</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 32  <div class="s0">                            </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">GCDA </div><div class="s2">&gt;</div><div class="s0"> GCDB</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 33  <div class="s0">                                GCDA </div><div class="s2">=</div><div class="s0"> GCDA</div><div class="s2">-</div><div class="s0">GCDB</div><div class="s2">;</div><div class="s0"></div>
 34  <div class="s0">                            </div><div class="s2">}</div><div class="s0"> </div><div class="s1">else</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 35  <div class="s0">                                GCDB </div><div class="s2">=</div><div class="s0"> GCDB</div><div class="s2">-</div><div class="s0">GCDA</div><div class="s2">;</div><div class="s0"></div>
 36  <div class="s0">                            </div><div class="s2">}</div><div class="s0"></div>
 37  <div class="s0">                            </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 38  <div class="s0">                        </div><div class="s2">}</div><div class="s0"></div>
 39  <div class="s0">                    </div><div class="s2">}</div><div class="s0"></div>
 40  <div class="s0">                    Response </div><div class="s2">=</div><div class="s0"> CalculatorResult</div><div class="s2">{</div><div class="s0">Value</div><div class="s2">:</div><div class="s0"> GCDA </div><div class="s2">|</div><div class="s0"> GCDB</div><div class="s2">}</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 41  <div class="s0">                </div><div class="s2">}</div><div class="s0"></div>
 42  <div class="s0">            </div><div class="s2">}</div><div class="s0"></div>
 43  <div class="s0">        </div><div class="s2">}</div><div class="s0"></div>
 44  <div class="s0">        </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 45  <div class="s0">    </div><div class="s2">}</div><div class="s0"></div>
 46  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
 47  <div class="s0"></div>
 48  <div class="s0"></div><div class="s1">system</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 49  <div class="s0">    </div><div class="s1">instance</div><div class="s0"> Calculator</div><div class="s2">:</div><div class="s0"> CalculatorTransform</div><div class="s2">;</div><div class="s0"></div>
 50  <div class="s0">    </div><div class="s1">queue</div><div class="s0"> HostMessageRouter</div><div class="s2">.</div><div class="s0">ToCalculator </div><div class="s2">-&gt;</div><div class="s0"> Calculator</div><div class="s2">.</div><div class="s0">Request</div><div class="s2">;</div><div class="s0"></div>
 51  <div class="s0">    </div><div class="s1">wire</div><div class="s0"> Calculator</div><div class="s2">.</div><div class="s0">Response </div><div class="s2">-&gt;</div><div class="s0"> HostMessageRouter</div><div class="s2">.</div><div class="s0">FromCalculator</div><div class="s2">;</div><div class="s0"></div>
 52  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
 53  <div class="s0"></div>
</pre>

On an FPGA, there is no sequence of instructions stored in RAM. Instead, Flows generates the following set of formulas
that describe next value for each register:

<pre>
Condition1 = RequestReader is CalculatorRequest
Condition2 = RequestReader.CalculatorRequest is GCDRequest
Condition3 = GCDA != GCDB
Condition4 = GCDA > GCDB

Next GCDA =
  RequestReader.CalculatorRequest.GCDRequest.A if (currently at beginning or line 44) and Condition1 and Condition2
  GCDA-GCDB if (currently at line 30 or 37) and Condition3 and Condition4
  GCDA otherwise
Next GCDB =
  RequestReader.CalculatorRequest.GCDRequest.B if (currently at beginning or line 44) and Condition1 and Condition2
  GCDB-GCDA if (currently at line 30 or 37) and Condition3 and !Condition4
  GCDB otherwise
</pre>

The state here is <code>GCDA</code>, <code>GCDB</code>, <code>Currently at beginning</code>, <code>Currently at line
30</code>, <code>Currently at line 37</code> and <code>Currently at line 44</code>. Lines 30, 37 and 44 are the `step`
statements, where the program waits for the next clock cycle; "currently at line 30" means the program is stopped at
that step.

One notable aspect of an FPGA (and hardware in general) is that every option for a formula is evaluated in parallel and
then final value is picked. Even if the next value for particular register happens to be the same as current value,
it is still assigned.

So what the Flows compiler does is transform the original source code into those formulas. [This page](Compiler.md) describes
in detail how (please note that it assumes familiarity with compiler construction).

## What the formulas become in hardware

Consider this code:

<style>
.s0 { display: inline; color: #c0c0c0 }
.s1 { display: inline; color: #80c080 }
.s2 { display: inline; color: #c08080 }
.s3 { display: inline; color: #808080 }
</style>
<pre>  1  <div class="s0"></div><div class="s1">struct</div><div class="s0"> SetIntervalCommand </div><div class="s2">{</div><div class="s0"></div>
  2  <div class="s0">    Interval</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
  3  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
  4  <div class="s0"></div>
  5  <div class="s0"></div><div class="s1">transform</div><div class="s0"> PingSenderTransform </div><div class="s2">(</div><div class="s0">NetworkOut</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">,</div><div class="s0"> MessageFromHost</div><div class="s2">:</div><div class="s0"> SetIntervalCommand</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
  6  <div class="s0">    Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"> Counter </div><div class="s2">=</div><div class="s0"> 0</div><div class="s2">;</div><div class="s0"></div>
  7  <div class="s0">    Bit</div><div class="s2">[</div><div class="s0">32</div><div class="s2">]</div><div class="s0"> Interval </div><div class="s2">=</div><div class="s0"> 32226562</div><div class="s2">;</div><div class="s0"></div>
  8  <div class="s0">    </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0"></div><div class="s1">true</div><div class="s0"></div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
  9  <div class="s0">        Counter </div><div class="s2">=</div><div class="s0"> 0</div><div class="s2">;</div><div class="s0"></div>
 10  <div class="s0">        </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 11  <div class="s0">        </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0">Counter </div><div class="s2">!=</div><div class="s0"> Interval</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 12  <div class="s0">            Counter </div><div class="s2">=</div><div class="s0"> Counter</div><div class="s2">+</div><div class="s0">1</div><div class="s2">;</div><div class="s0"></div>
 13  <div class="s0">            </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 14  <div class="s0">        </div><div class="s2">}</div><div class="s0"></div>
 15  <div class="s0">        </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 16  <div class="s0">        </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">MessageFromHostReader </div><div class="s1">is</div><div class="s0"> SetIntervalCommand</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 17  <div class="s0">            Counter </div><div class="s2">=</div><div class="s0"> 0</div><div class="s2">;</div><div class="s0"></div>
 18  <div class="s0">            Interval </div><div class="s2">=</div><div class="s0"> MessageFromHostReader</div><div class="s2">.</div><div class="s0">SetIntervalCommand</div><div class="s2">.</div><div class="s0">Interval</div><div class="s2">;</div><div class="s0"></div>
 19  <div class="s0">            MessageFromHostPop </div><div class="s2">=</div><div class="s0"> 1</div><div class="s2">;</div><div class="s0"></div>
 20  <div class="s0">        </div><div class="s2">}</div><div class="s0"></div>
 21  <div class="s0">        </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 22  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x00005e00</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 23  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x53010000</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 24  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x5e005302</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 25  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x08004500</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 26  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x00320000</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 27  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x00004001</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 28  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x66640a00</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 29  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x00600a00</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 30  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x00080800</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 31  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x5b560001</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 32  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x02030405</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 33  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x06070809</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 34  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x0a0b0c0d</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 35  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x0e0f1011</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 36  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x12131415</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 0</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 37  <div class="s0">        BlockingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> NetworkOut</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">{</div><div class="s0">Data</div><div class="s2">:</div><div class="s0"> 0x16171819</div><div class="s2">,</div><div class="s0"> ByteCount</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> Last</div><div class="s2">:</div><div class="s0"> 1</div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 38  <div class="s0">    </div><div class="s2">}</div><div class="s0"></div>
 39  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
 40  <div class="s0"></div>
 41  <div class="s0"></div><div class="s1">system</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 42  <div class="s0">    </div><div class="s1">instance</div><div class="s0"> PingSender</div><div class="s2">:</div><div class="s0"> PingSenderTransform</div><div class="s2">;</div><div class="s0"></div>
 43  <div class="s0">    </div><div class="s3">/* Native module connecting to Ethernet port */</div><div class="s0"></div>
 44  <div class="s0">    </div><div class="s1">wire</div><div class="s0"> </div><div class="s1">with</div><div class="s0"> </div><div class="s1">backpressure</div><div class="s0"> PingSender</div><div class="s2">.</div><div class="s0">NetworkOut </div><div class="s2">-&gt;</div><div class="s0"> Ethernet</div><div class="s2">.</div><div class="s0">Send</div><div class="s2">;</div><div class="s0"></div>
 45  <div class="s0">    </div><div class="s3">/* and to PCIe host message dispatcher */</div><div class="s0"></div>
 46  <div class="s0">    </div><div class="s1">queue</div><div class="s0"> HostMessageRouter</div><div class="s2">.</div><div class="s0">ToPingSender </div><div class="s2">-&gt;</div><div class="s0"> PingSender</div><div class="s2">.</div><div class="s0">MessageFromHost</div><div class="s2">;</div><div class="s0"></div>
 47  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
 48  <div class="s0"></div>
</pre>

Each BlockingPush on lines 22–37 waits for the receiver, so it contains an implicit step and gets its own "currently at"
register, named after its line (`Current_reg[A22_9step] … [A37_9step]`). Since each push simply follows the previous one,
the registers form a chain: each register's output is the next one's input, and the last one drives `NetworkOutWord.Last`
directly.

![16 step registers chained Q → D](PeriodicPingerSchematic.svg)

Not shown: logic that generates the packet bytes from these registers, and the interval counter; dashed lines mark nets
with hidden connections.
